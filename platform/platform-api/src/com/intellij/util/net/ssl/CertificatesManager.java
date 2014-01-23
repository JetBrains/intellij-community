package com.intellij.util.net.ssl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code CertificatesManager} is responsible for negotiation SSL connection with server
 * and deals with untrusted/self-singed/expired and other kinds of digital certificates.
 * <h1>Integration details:</h1>
 * If you're using httpclient-3.1 without custom {@code Protocol} instance for HTTPS you don't have to do anything
 * at all: default {@code HttpClient} will use "Default" {@code SSLContext}, which is set up by this component itself.
 * <p/>
 * However for httpclient-4.x you have several of choices:
 * <ol>
 * <li>Client returned by {@code HttpClients.createSystem()} will use "Default" SSL context as it does in httpclient-3.1.</li>
 * <li>If you want to customize {@code HttpClient} using {@code HttpClients.custom()}, you can use the following methods of the builder
 * (in the order of increasing complexity/flexibility)
 * <ol>
 * <li>{@code useSystemProperties()} methods makes {@code HttpClient} use "Default" SSL context again</li>
 * <li>{@code setSSLContext()} and pass result of the {@link #getSslContext()}</li>
 * <li>{@code setSSLSocketFactory()} and specify instance {@code SSLConnectionSocketFactory} which uses result of {@link #getSslContext()}.</li>
 * <li>{@code setConnectionManager} and initialize it with {@code Registry} that binds aforementioned {@code SSLConnectionSocketFactory} to HTTPS protocol</li>
 * </ol>
 * </li>
 * </ol>
 *
 * @author Mikhail Golubev
 */
public class CertificatesManager implements ApplicationComponent {

  @NonNls public static final String COMPONENT_NAME = "Certificates Manager";
  @NonNls private static final String DEFAULT_PATH = FileUtil.join(PathManager.getSystemPath(), "tasks", "cacerts");
  @NonNls private static final String DEFAULT_PASSWORD = "changeit";

  private static final Logger LOG = Logger.getInstance(CertificatesManager.class);
  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  /**
   * Special version of hostname verifier, that asks user whether he accepts certificate, which subject's common name
   * doesn't match requested hostname.
   */
  public static final HostnameVerifier HOSTNAME_VERIFIER = new MyHostnameVerifier();

  public static CertificatesManager getInstance() {
    return (CertificatesManager)ApplicationManager.getApplication().getComponent(COMPONENT_NAME);
  }

  private final String myCacertsPath;
  private final String myPassword;
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
    SSLContext context = getSystemSslContext();
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init((KeyStore)null);
    // assume that only X509 TrustManagers exist
    X509TrustManager systemManager = findX509TrustManager(factory.getTrustManagers());
    assert systemManager != null;

    // can also check, that default trust store exists, on this step
    //assert systemManager.getAcceptedIssuers().length != 0

    MutableX509TrustManager customManager = new MutableX509TrustManager(myCacertsPath, myPassword);
    MyX509TrustManager trustManager = new MyX509TrustManager(systemManager, customManager);
    // use default key store and secure random
    context.init(null, new TrustManager[]{trustManager}, null);
    return context;
  }

  @NotNull
  public static SSLContext getSystemSslContext() {
    // NOTE SSLContext.getDefault() should not be called because it automatically creates
    // default context with can't be initialized twice
    try {
      // actually TLSv1 support is mandatory for Java platform
      return SSLContext.getInstance("TLS");
    }
    catch (NoSuchAlgorithmException e) {
      LOG.error(e);
      throw new IllegalStateException("Can't get system SSL context");
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

  private static class MyX509TrustManager implements X509TrustManager {
    private final X509TrustManager mySystemManager;
    private final MutableX509TrustManager myCustomManager;


    private MyX509TrustManager(X509TrustManager system, MutableX509TrustManager custom) {
      mySystemManager = system;
      myCustomManager = custom;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      // Not called by client
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] certificates, String s) throws CertificateException {
      try {
        mySystemManager.checkServerTrusted(certificates, s);
      }
      catch (RuntimeException e) {
        Throwable cause = e.getCause();
        // this can happen on some version of Apple's JRE, e.g. see IDEA-115565
        if (cause != null && cause.getMessage().equals("the trustAnchors parameter must be non-empty")) {
          LOG.error("It seems, that your JRE installation doesn't have system trust store.\n" +
                    "If you're using Mac JRE, try upgrading to the latest version.", e);
        }
      }
      catch (CertificateException e) {
        X509Certificate certificate = certificates[0];
        // looks like self-signed certificate
        if (certificates.length == 1) {
          // check-then-act sequence
          synchronized (myCustomManager) {
            try {
              myCustomManager.checkServerTrusted(certificates, s);
            }
            catch (CertificateException e2) {
              if (myCustomManager.isBroken() || !updateTrustStore(certificate)) {
                throw e;
              }
            }
          }
        }
      }
    }

    private boolean updateTrustStore(final X509Certificate certificate) {
      Application app = ApplicationManager.getApplication();
      if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
        myCustomManager.addCertificate(certificate);
        return true;
      }
      boolean accepted = showAcceptDialog(new Callable<DialogWrapper>() {
        @Override
        public DialogWrapper call() throws Exception {
          return CertificateWarningDialog.createSelfSignedCertificateWarning(certificate);
        }
      });
      if (accepted) {
        LOG.debug("Certificate was accepted");
        myCustomManager.addCertificate(certificate);
      }
      return accepted;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return mySystemManager.getAcceptedIssuers();
    }
  }

  /**
   * Trust manager that supports addition of new certificates (most likely self-signed) to corresponding physical
   * key store.
   */
  private static class MutableX509TrustManager implements X509TrustManager {
    private final String myPath;
    private final String myPassword;
    private final TrustManagerFactory myFactory;
    private final KeyStore myKeyStore;
    private volatile X509TrustManager myTrustManager;
    private volatile boolean broken = false;

    private MutableX509TrustManager(@NotNull String path, @NotNull String password) {
      myPath = path;
      myPassword = password;
      myKeyStore = loadKeyStore(path, password);
      myFactory = createFactory();
      myTrustManager = initFactoryAndGetManager();
    }

    private TrustManagerFactory createFactory() {
      try {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      }
      catch (NoSuchAlgorithmException e) {
        LOG.error(e);
        broken = true;
      }
      return null;
    }

    private KeyStore loadKeyStore(String path, String password) {
      KeyStore keyStore = null;
      try {
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        File cacertsFile = new File(path);
        if (cacertsFile.exists()) {
          FileInputStream stream = null;
          try {
            stream = new FileInputStream(path);
            keyStore.load(stream, password.toCharArray());
          }
          finally {
            StreamUtil.closeStream(stream);
          }
        }
        else {
          FileUtil.createParentDirs(cacertsFile);
          keyStore.load(null, password.toCharArray());
        }
      }
      catch (Exception e) {
        LOG.error(e);
        broken = true;
      }
      return keyStore;
    }

    public boolean addCertificate(X509Certificate certificate) {
      if (broken) {
        return false;
      }
      String alias = certificate.getIssuerX500Principal().getName();
      FileOutputStream stream = null;
      try {
        myKeyStore.setCertificateEntry(alias, certificate);
        stream = new FileOutputStream(myPath);
        myKeyStore.store(stream, myPassword.toCharArray());
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        return true;
      }
      catch (Exception e) {
        LOG.error(e);
        return false;
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      if (keyStoreIsEmpty() || broken) {
        throw new CertificateException();
      }
      myTrustManager.checkClientTrusted(certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      if (keyStoreIsEmpty() || broken) {
        throw new CertificateException();
      }
      myTrustManager.checkServerTrusted(certificates, s);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      // trust no one if broken
      if (keyStoreIsEmpty() || broken) {
        return NO_CERTIFICATES;
      }
      return myTrustManager.getAcceptedIssuers();
    }

    private boolean keyStoreIsEmpty() {
      try {
        return myKeyStore.size() == 0;
      }
      catch (KeyStoreException e) {
        LOG.error(e);
        return true;
      }
    }

    private X509TrustManager initFactoryAndGetManager() {
      if (!broken) {
        try {
          myFactory.init(myKeyStore);
          return findX509TrustManager(myFactory.getTrustManagers());
        }
        catch (KeyStoreException e) {
          LOG.error(e);
          broken = true;
        }
      }
      return null;
    }

    public boolean isBroken() {
      return broken;
    }
  }

  private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager)manager;
      }
    }
    return null;
  }

  /*
   * BrowserCompatibleHostnameVerifier has all final/package-private methods, so direct
   * inheritance from it makes no sense. Inheriting from AbstractVerifier also makes no sense, because
   * the only method I can override verify(String, String[], String[]), and I have no access to certificate for the dialog
   * in this case. Why the heck verify(String, X509Certificate) is final? It basically means, that I should copy half of the
   * AbstractVerifier here, which gives me a strong feel of stupidity.
   *
   * I submit a bug about this problem https://issues.apache.org/jira/browse/HTTPCLIENT-1449 and it seems to be
   * resolved in httpclient4.4.
   */
  private static class MyHostnameVerifier implements X509HostnameVerifier {
    private static final X509HostnameVerifier DELEGATE = SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
    private static final boolean CHECK_HOSTNAME = false;

    // Copied from httpclient 4.2 sources, read class level commentary for explanation.
    @Override
    public void verify(String host, SSLSocket ssl) throws IOException {
      if (host == null) {
        throw new NullPointerException("host to verify is null");
      }

      SSLSession session = ssl.getSession();
      if (session == null) {
        // In our experience this only happens under IBM 1.4.x when
        // spurious (unrelated) certificates show up in the server'
        // chain.  Hopefully this will unearth the real problem:
        final InputStream in = ssl.getInputStream();
        in.available();
        // If ssl.getInputStream().available() didn't cause an
        // exception, maybe at least now the session is available?
        session = ssl.getSession();
        if (session == null) {
          // If it's still null, probably a startHandshake() will
          // unearth the real problem.
          ssl.startHandshake();

          // Okay, if we still haven't managed to cause an exception,
          // might as well go for the NPE.  Or maybe we're okay now?
          session = ssl.getSession();
        }
      }

      final Certificate[] certs = session.getPeerCertificates();
      final X509Certificate x509 = (X509Certificate)certs[0];
      verify(host, x509);
    }

    @Override
    public void verify(final String host, final X509Certificate cert) throws SSLException {
        try {
          DELEGATE.verify(host, cert);
        }
        catch (SSLException e) {
          //noinspection ConstantConditions
          if (!(CHECK_HOSTNAME && showAcceptDialog(new Callable<DialogWrapper>() {
            @Override
            public DialogWrapper call() throws Exception {
              return CertificateWarningDialog.createHostnameMismatchWarning(cert, host);
            }
          }))) {
            throw e;
          }
          // TODO: inclusion in some kind of persistent settings
          // Read/Write lock to protect storage?
        }
    }

    // Copied from httpclient 4.2 sources, read class level commentary for explanation.
    @Override
    public boolean verify(String host, SSLSession session) {
      try {
        final Certificate[] certs = session.getPeerCertificates();
        final X509Certificate x509 = (X509Certificate)certs[0];
        verify(host, x509);
        return true;
      }
      catch (final SSLException e) {
        return false;
      }
    }

    @Override
    public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
      // actually never used, because it's only used in verify(final String host, final X509Certificate cert)
      DELEGATE.verify(host, cns, subjectAlts);
    }
  }

  private static boolean showAcceptDialog(final @NotNull Callable<? extends DialogWrapper> dialogFactory) {
    Application app = ApplicationManager.getApplication();
    final CountDownLatch proceeded = new CountDownLatch(1);
    final AtomicBoolean accepted = new AtomicBoolean();
    app.invokeLater(new Runnable() {
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
    }, ModalityState.any());
    try {
      proceeded.await();
    }
    catch (InterruptedException e) {
      LOG.error("Interrupted", e);
    }
    return accepted.get();
  }
}
