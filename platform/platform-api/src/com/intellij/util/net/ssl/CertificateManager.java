/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.net.ssl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.BadPaddingException;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code CertificateManager} is responsible for negotiation SSL connection with server
 * and deals with untrusted/self-singed/expired and other kinds of digital certificates.
 * <h1>Integration details:</h1>
 * If you're using httpclient-3.1 without custom {@code Protocol} instance for HTTPS you don't have to do anything
 * at all: default {@code HttpClient} will use "Default" {@code SSLContext}, which is set up by this component itself.
 * <p/>
 * However for httpclient-4.x you have several of choices:
 * <pre>
 * <ol>
 *  <li>Client returned by {@code HttpClients.createSystem()} will use "Default" SSL context as it does in httpclient-3.1.</li>
 *  <li>If you want to customize {@code HttpClient} using {@code HttpClients.custom()}, you can use the following methods of the builder
 *  (in the order of increasing complexity/flexibility)
 *    <ol>
 *      <li>{@code useSystemProperties()} methods makes {@code HttpClient} use "Default" SSL context again</li>
 *      <li>{@code setSSLContext()} and pass result of the {@link #getSslContext()}</li>
 *      <li>{@code setSSLSocketFactory()} and specify instance {@code SSLConnectionSocketFactory} which uses result of {@link #getSslContext()}.</li>
 *      <li>{@code setConnectionManager} and initialize it with {@code Registry} that binds aforementioned {@code SSLConnectionSocketFactory} to HTTPS protocol</li>
 *      </ol>
 *    </li>
 * </ol>
 * </pre>
 *
 * @author Mikhail Golubev
 */
@State(
  name = "CertificateManager",
  storages = {
    @Storage("certificates.xml"),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class CertificateManager implements PersistentStateComponent<CertificateManager.Config> {

  @NonNls public static final String COMPONENT_NAME = "Certificate Manager";
  @NonNls private static final String DEFAULT_PATH = FileUtil.join(PathManager.getSystemPath(), "tasks", "cacerts");
  @NonNls private static final String DEFAULT_PASSWORD = "changeit";

  private static final Logger LOG = Logger.getInstance(CertificateManager.class);

  /**
   * Special version of hostname verifier, that asks user whether he accepts certificate, which subject's common name
   * doesn't match requested hostname.
   */
  public static final HostnameVerifier HOSTNAME_VERIFIER = new ConfirmingHostnameVerifier(SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
  /**
   * Used to check whether dialog is visible to prevent possible deadlock, e.g. when some external resource is loaded by
   * {@link java.awt.MediaTracker}.
   */
  static final long DIALOG_VISIBILITY_TIMEOUT = 5000; // ms

  public static CertificateManager getInstance() {
    return ApplicationManager.getApplication().getComponent(CertificateManager.class);
  }

  private final String myCacertsPath;
  private final String myPassword;
  private final Config myConfig;

  private final ConfirmingTrustManager myTrustManager;

  /**
   * Lazy initialized
   */
  private SSLContext mySslContext;

  /**
   * Component initialization constructor
   */
  public CertificateManager() {
    myCacertsPath = DEFAULT_PATH;
    myPassword = DEFAULT_PASSWORD;
    myConfig = new Config();
    myTrustManager = ConfirmingTrustManager.createForStorage(myCacertsPath, myPassword);

    try {
      // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
      // Protocol.registerProtocol("https", CertificateManager.createDefault().createProtocol());
      if (Registry.is("ide.certificate.manager")) {
        SSLContext.setDefault(getSslContext());
        LOG.debug("Default SSL context initialized");
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  /**
   * Creates special kind of {@code SSLContext}, which X509TrustManager first checks certificate presence in
   * in default system-wide trust store (usually located at {@code ${JAVA_HOME}/lib/security/cacerts} or specified by
   * {@code javax.net.ssl.trustStore} property) and when in the one specified by field {@link #myCacertsPath}.
   * If certificate wasn't found in either, manager will ask user, whether it can be
   * accepted (like web-browsers do) and then, if it does, certificate will be added to specified trust store.
   * <p/>
   * If any error occurred during creation its message will be logged and system default SSL context will be returned
   * so clients don't have to deal with awkward JSSE errors.
   * </p>
   * This method may be used for transition to HttpClient 4.x (see {@code HttpClientBuilder#setSslContext(SSLContext)})
   * and {@code org.apache.http.conn.ssl.SSLConnectionSocketFactory()}.
   *
   * @return instance of SSLContext with described behavior or default SSL context in case of error
   */
  @NotNull
  public synchronized SSLContext getSslContext() {
    if (mySslContext == null) {
      SSLContext context = getSystemSslContext();
      if (Registry.is("ide.certificate.manager")) {
        try {
          // SSLContext context = SSLContext.getDefault();
          // NOTE: existence of default trust manager can be checked here as
          // assert systemManager.getAcceptedIssuers().length != 0
          context.init(getDefaultKeyManagers(), new TrustManager[]{getTrustManager()}, null);
        }
        catch (KeyManagementException e) {
          LOG.error(e);
        }
      }
      else {
        // IDEA-124057 Do not touch default context at all if certificate manager was disabled.

        // For some reason passing `null` as first parameter of SSLContext#init is not enough to
        // use -Djavax.net.ssl.keyStore VM parameters, although -Djavax.net.ssl.trustStore is used
        // successfully. See this question on Stackoverflow for details
        // http://stackoverflow.com/questions/23205266/java-key-store-is-not-found-when-default-ssl-context-is-redefined
        context = getDefaultSslContext();
      }
      mySslContext = context;
    }
    return mySslContext;
  }

  @NotNull
  public static SSLContext getSystemSslContext() {
    // NOTE: SSLContext.getDefault() should not be called because it automatically creates
    // default context which can't be initialized twice
    try {
      // actually TLSv1 support is mandatory for Java platform
      SSLContext context = SSLContext.getInstance(CertificateUtil.TLS);
      context.init(null, null, null);
      return context;
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
      throw new AssertionError("Cannot get system SSL context");
    }
    catch (KeyManagementException e) {
      LOG.error(e);
      throw new AssertionError("Cannot initialize system SSL context");
    }
  }

  @NotNull
  private static SSLContext getDefaultSslContext() {
    try {
      return SSLContext.getDefault();
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error("Default SSL context not available. Using system instead.");
      return getSystemSslContext();
    }
  }

  /**
   * Workaround for IDEA-124057. Manually find key store specified via VM options.
   *
   * @return key managers or {@code null} in case of any error
   */
  @Nullable
  public static KeyManager[] getDefaultKeyManagers() {
    String keyStorePath = System.getProperty("javax.net.ssl.keyStore");
    if (keyStorePath != null) {
      LOG.info("Loading custom key store specified with VM options: " + keyStorePath);
      try {
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore keyStore;
        String keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
        try {
          keyStore = KeyStore.getInstance(keyStoreType);
        }
        catch (KeyStoreException e) {
          if (e.getCause() instanceof NoSuchAlgorithmException) {
            LOG.error("Wrong key store type: " + keyStoreType, e);
            return null;
          }
          throw e;
        }
        String password = System.getProperty("javax.net.ssl.keyStorePassword", "");
        InputStream inputStream = null;
        try {
          inputStream = new FileInputStream(keyStorePath);
          keyStore.load(inputStream, password.toCharArray());
          factory.init(keyStore, password.toCharArray());
        }
        catch (FileNotFoundException e) {
          LOG.error("Key store file not found: " + keyStorePath);
          return null;
        }
        catch (Exception e) {
          if (e.getCause() instanceof BadPaddingException) {
            LOG.error("Wrong key store password: " + password, e);
            return null;
          }
          throw e;
        }
        finally {
          StreamUtil.closeStream(inputStream);
        }
        return factory.getKeyManagers();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return null;
  }

  @NotNull
  public String getCacertsPath() {
    return myCacertsPath;
  }

  @NotNull
  public String getPassword() {
    return myPassword;
  }

  @NotNull
  public ConfirmingTrustManager getTrustManager() {
    return myTrustManager;
  }

  @NotNull
  public ConfirmingTrustManager.MutableTrustManager getCustomTrustManager() {
    return myTrustManager.getCustomManager();
  }

  public static boolean showAcceptDialog(final @NotNull Callable<? extends DialogWrapper> dialogFactory) {
    Application app = ApplicationManager.getApplication();
    final CountDownLatch proceeded = new CountDownLatch(1);
    final AtomicBoolean accepted = new AtomicBoolean();
    final AtomicReference<DialogWrapper> dialogRef = new AtomicReference<>();
    Runnable showDialog = () -> {
      // skip if certificate was already rejected due to timeout or interrupt
      if (proceeded.getCount() == 0) {
        return;
      }
      try {
        DialogWrapper dialog = dialogFactory.call();
        dialogRef.set(dialog);
        accepted.set(dialog.showAndGet());
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        proceeded.countDown();
      }
    };
    if (app.isDispatchThread()) {
      showDialog.run();
    }
    else {
      app.invokeLater(showDialog, ModalityState.any());
    }
    try {
      // IDEA-123467 and IDEA-123335 workaround
      boolean inTime = proceeded.await(DIALOG_VISIBILITY_TIMEOUT, TimeUnit.MILLISECONDS);
      if (!inTime) {
        DialogWrapper dialog = dialogRef.get();
        if (dialog == null || !dialog.isShowing()) {
          LOG.debug("After " + DIALOG_VISIBILITY_TIMEOUT + " ms dialog was not shown. " +
                    "Rejecting certificate. Current thread: " + Thread.currentThread().getName());
          proceeded.countDown();
          return false;
        }
        else {
          proceeded.await(); // if dialog is already shown continue waiting
        }
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      proceeded.countDown();
    }
    return accepted.get();
  }

  @NotNull
  @Override
  public Config getState() {
    return myConfig;
  }

  @Override
  public void loadState(Config state) {
    XmlSerializerUtil.copyBean(state, myConfig);
  }

  public static class Config {
    /**
     * Ensure that request's hostname matches certificate's common name (CN).
     */
    public boolean CHECK_HOSTNAME = false;
    /**
     * Ensure that certificate is neither expired nor not yet eligible.
     */
    public boolean CHECK_VALIDITY = false;

    @Tag("expired")
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "commonName")
    public LinkedHashSet<String> BROKEN_CERTIFICATES = new LinkedHashSet<>();

    /**
     * Do not show the dialog and accept untrusted certificates automatically.
     */
    public boolean ACCEPT_AUTOMATICALLY = false;
  }
}
