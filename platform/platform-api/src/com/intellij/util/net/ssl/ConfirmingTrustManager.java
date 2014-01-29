package com.intellij.util.net.ssl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Special kind of trust manager, that asks user to confirm untrusted certificate, e.g. if
 * it wasn't found in system-wide storage.
 *
 * @author Mikhail Golubev
 */
class ConfirmingTrustManager implements X509TrustManager {
  private static final Logger LOG = Logger.getInstance(ConfirmingTrustManager.class);
  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  // Errors
  @NonNls public static final String ERR_EMPTY_TRUST_ANCHORS = "It seems, that your JRE installation doesn't have system trust store.\n" +
                                                               "If you're using Mac JRE, try upgrading to the latest version.";


  public static ConfirmingTrustManager createForStorage(@NotNull String path, @NotNull String password) {
    return new ConfirmingTrustManager(getSystemDefault(), new MutableTrustManager(path, password));
  }

  private static X509TrustManager getSystemDefault() {
    X509TrustManager systemManager;
    try {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init((KeyStore)null);
      // assume that only X509 TrustManagers exist
      systemManager = findX509TrustManager(factory.getTrustManagers());
    }
    catch (Exception e) {
      throw new AssertionError(e);
    }
    return systemManager;
  }

  private final X509TrustManager mySystemManager;
  private final MutableTrustManager myCustomManager;


  private ConfirmingTrustManager(X509TrustManager system, MutableTrustManager custom) {
    mySystemManager = system;
    myCustomManager = custom;
  }

  static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager)manager;
      }
    }
    return null;
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
        LOG.error(ERR_EMPTY_TRUST_ANCHORS, e);
        Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                                  "No default keystore", ERR_EMPTY_TRUST_ANCHORS,
                                                  NotificationType.ERROR));
        throw e;
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
    boolean accepted = CertificatesManager.showAcceptDialog(new Callable<DialogWrapper>() {
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

  public X509TrustManager getSystemManager() {
    return mySystemManager;
  }

  public MutableTrustManager getCustomManager() {
    return myCustomManager;
  }

  /**
   * Trust manager that supports addition of new certificates (most likely self-signed) to corresponding physical
   * key store.
   */
  static class MutableTrustManager implements X509TrustManager {
    private final String myPath;
    private final String myPassword;
    private final TrustManagerFactory myFactory;
    private final KeyStore myKeyStore;
    private X509TrustManager myTrustManager;
    private volatile boolean broken = false;
    private final ReadWriteLock myLock = new ReentrantReadWriteLock();
    private final Lock myReadLock = myLock.readLock();
    private final Lock myWriteLock = myLock.writeLock();

    private MutableTrustManager(@NotNull String path, @NotNull String password) {
      myPath = path;
      myPassword = password;
      // initialization step
      myWriteLock.lock();
      try {
        myKeyStore = loadKeyStore(path, password);
        myFactory = createFactory();
        myTrustManager = initFactoryAndGetManager();
      }
      finally {
        myWriteLock.unlock();
      }
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

    private KeyStore loadKeyStore(@NotNull String path, @NotNull String password) {
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


    /**
     * Add certificate to underlying trust store.
     *
     * @param certificate   server's certificate
     * @return              whether the operation was successful
     */
    public boolean addCertificate(@NotNull X509Certificate certificate) {
      if (broken) {
        return false;
      }
      FileOutputStream stream = null;
      myWriteLock.lock();
      try {
        myKeyStore.setCertificateEntry(createAlias(certificate), certificate);
        stream = new FileOutputStream(myPath);
        myKeyStore.store(stream, myPassword.toCharArray());
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        return true;
      }
      catch (Exception e) {
        LOG.error("Can't add certificate", e);
        return false;
      }
      finally {
        StreamUtil.closeStream(stream);
        myWriteLock.unlock();
      }
    }

    /**
     * Add certificate, loaded from file at {@code path}, to underlying trust store.
     *
     * @param path  path to file containing certificate
     * @return      whether the operation was successful
     */
    public boolean addCertificate(@NotNull String path) {
      X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
      return certificate != null && addCertificate(certificate);
    }

    private static String createAlias(@NotNull X509Certificate certificate) {
      return certificate.getIssuerX500Principal().getName();
    }

    /**
     * Remove certificate from underlying trust store.
     *
     * @param certificate certificate alias
     * @return            whether the operation was successful
     */
    public boolean removeCertificate(@NotNull X509Certificate certificate) {
      return removeCertificate(createAlias(certificate));
    }

    /**
     * Remove certificate, specified by its alias, from underlying trust store.
     *
     * @param alias certificate's alias
     * @return      true if removal operation was successful and false otherwise
     */
    public boolean removeCertificate(@NotNull String alias) {
      if (broken) {
        return false;
      }
      FileOutputStream stream = null;
      myWriteLock.lock();
      try {
        myKeyStore.deleteEntry(alias);
        stream = new FileOutputStream(myPath);
        myKeyStore.store(stream, myPassword.toCharArray());
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        return true;
      }
      catch (Exception e) {
        LOG.error("Can't remove certificate for alias: " + alias, e);
        return false;
      }
      finally {
        StreamUtil.closeStream(stream);
        myWriteLock.unlock();
      }
    }

    /**
     * Get certificate, specified by its alias, from underlying trust store.
     *
     * @param alias certificate's alias
     * @return certificate or null if it's not present
     */
    @Nullable
    public X509Certificate getCertificate(@NotNull String alias) {
      myReadLock.lock();
      try {
        return (X509Certificate)myKeyStore.getCertificate(alias);
      }
      catch (KeyStoreException e) {
        return null;
      }
      finally {
        myReadLock.unlock();
      }
    }

    /**
     * Select all available certificates from underlying trust store. Result list is not supposed to be modified.
     *
     * @return certificates
     */
    public List<X509Certificate> getCertificates() {
      myReadLock.lock();
      List<X509Certificate> certificates = new ArrayList<X509Certificate>();
      try {
        Iterator<String> iterator = ContainerUtil.iterate(myKeyStore.aliases());
        while (iterator.hasNext()) {
          certificates.add(getCertificate(iterator.next()));
        }
        return ContainerUtil.immutableList(certificates);
      }
      catch (Exception e) {
        LOG.error(e);
        return ContainerUtil.emptyList();
      }
      finally {
        myReadLock.unlock();
      }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      Lock readLock = myLock.readLock();
      try {
        if (keyStoreIsEmpty() || broken) {
          throw new CertificateException();
        }
        myTrustManager.checkClientTrusted(certificates, s);
      }
      finally {
        readLock.unlock();
      }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      myReadLock.lock();
      try {
        if (keyStoreIsEmpty() || broken) {
          throw new CertificateException();
        }
        myTrustManager.checkServerTrusted(certificates, s);
      }
      finally {
        myReadLock.unlock();
      }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      myReadLock.lock();
      try {
        // trust no one if broken
        if (keyStoreIsEmpty() || broken) {
          return NO_CERTIFICATES;
        }
        return myTrustManager.getAcceptedIssuers();
      }
      finally {
        myReadLock.unlock();
      }
    }

    private boolean keyStoreIsEmpty() {
      myReadLock.lock();
      try {
        return myKeyStore.size() == 0;
      }
      catch (KeyStoreException e) {
        LOG.error(e);
        return true;
      }
      finally {
        myReadLock.unlock();
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
}
