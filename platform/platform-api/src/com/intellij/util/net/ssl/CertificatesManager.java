package com.intellij.util.net.ssl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.http.conn.ssl.SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;

/**
 * {@code CertificatesManager} is responsible for negotiation SSL connection with server
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
  name = "CertificatesManager",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml")
)
public class CertificatesManager implements ApplicationComponent, PersistentStateComponent<CertificatesManager.Config> {

  @NonNls public static final String COMPONENT_NAME = "Certificates Manager";
  @NonNls private static final String DEFAULT_PATH = FileUtil.join(PathManager.getSystemPath(), "tasks", "cacerts");
  @NonNls private static final String DEFAULT_PASSWORD = "changeit";

  private static final Logger LOG = Logger.getInstance(CertificatesManager.class);

  /**
   * Special version of hostname verifier, that asks user whether he accepts certificate, which subject's common name
   * doesn't match requested hostname.
   */
  public static final HostnameVerifier HOSTNAME_VERIFIER = new ConfirmingHostnameVerifier(BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);

  public static CertificatesManager getInstance() {
    return (CertificatesManager)ApplicationManager.getApplication().getComponent(COMPONENT_NAME);
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
  public CertificatesManager() {
    myCacertsPath = DEFAULT_PATH;
    myPassword = DEFAULT_PASSWORD;
    myConfig = new Config();
    myTrustManager = ConfirmingTrustManager.createForStorage(myCacertsPath, myPassword);
  }

  @Override
  public void initComponent() {
    try {
      // Don't do this: protocol created this way will ignore SSL tunnels. See IDEA-115708.
      // Protocol.registerProtocol("https", CertificatesManager.createDefault().createProtocol());
      SSLContext.setDefault(getSslContext());
      LOG.debug("Default SSL context initialized");
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void disposeComponent() {
    // empty
  }

  @NotNull
  @Override
  public String getComponentName() {
    return COMPONENT_NAME;
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
      try {
        mySslContext = createSslContext();
      }
      catch (Exception e) {
        LOG.error(e);
        mySslContext = getSystemSslContext();
      }
    }
    return mySslContext;
  }

  @NotNull
  public SSLContext createSslContext() throws Exception {
    // SSLContext context = SSLContext.getDefault();
    // can also check, that default trust store exists, on this step
    //assert systemManager.getAcceptedIssuers().length != 0

    SSLContext context = getSystemSslContext();
    context.init(null, new TrustManager[]{getTrustManager()}, null);
    return context;
  }

  @NotNull
  public static SSLContext getSystemSslContext() {
    // NOTE SSLContext.getDefault() should not be called because it automatically creates
    // default context with can't be initialized twice
    try {
      // actually TLSv1 support is mandatory for Java platform
      return SSLContext.getInstance(CertificateUtil.TLS);
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
      throw new AssertionError("Can't get system SSL context");
    }
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
    Runnable showDialog = new Runnable() {
      @Override
      public void run() {
        try {
          DialogWrapper dialog = dialogFactory.call();
          accepted.set(dialog.showAndGet());
        }
        catch (Exception e) {
          LOG.error("Unexpected error", e);
        }
        finally {
          proceeded.countDown();
        }
      }
    };
    if (app.isDispatchThread()) {
      showDialog.run();
    }
    else {
      app.invokeLater(showDialog, ModalityState.any());
    }
    try {
      proceeded.await();
    }
    catch (InterruptedException e) {
      LOG.error("Interrupted", e);
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
    // ensure that request's hostname matches certificate's common name (CN)
    public volatile boolean checkHostname;
    // ensure that certificate is neither expired nor not yet eligible
    public volatile boolean checkValidity;
    @Tag("expired")
    @Property(surroundWithTag = false)
    @AbstractCollection(elementTag = "commonName")
    public volatile LinkedHashSet<String> brokenCertificates = new LinkedHashSet<String>();
  }
}
