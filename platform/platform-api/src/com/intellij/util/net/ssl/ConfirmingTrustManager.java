/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The central piece of our SSL support - special kind of trust manager, that asks user to confirm
 * untrusted certificate, e.g. if it wasn't found in system-wide storage.
 *
 * @author Mikhail Golubev
 */
public class ConfirmingTrustManager extends ClientOnlyTrustManager {
  private static final Logger LOG = Logger.getInstance(ConfirmingTrustManager.class);
  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];
  private static final X509TrustManager MISSING_TRUST_MANAGER = new ClientOnlyTrustManager() {
    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      LOG.debug("Trust manager is missing. Retreating.");
      throw new CertificateException("Missing trust manager");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return NO_CERTIFICATES;
    }
  };

  public static ConfirmingTrustManager createForStorage(@NotNull String path, @NotNull String password) {
    return new ConfirmingTrustManager(getSystemDefault(), new MutableTrustManager(path, password));
  }

  private static X509TrustManager getSystemDefault() {
    try {
      TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      // hacky way to get default trust store
      factory.init((KeyStore)null);
      // assume that only X509 TrustManagers exist
      X509TrustManager systemManager = findX509TrustManager(factory.getTrustManagers());
      if (systemManager != null && systemManager.getAcceptedIssuers().length != 0) {
        return systemManager;
      }
    }
    catch (Exception e) {
      LOG.error("Cannot get system trust store", e);
    }
    return MISSING_TRUST_MANAGER;
  }

  private final X509TrustManager mySystemManager;
  private final MutableTrustManager myCustomManager;


  private ConfirmingTrustManager(X509TrustManager system, MutableTrustManager custom) {
    mySystemManager = system;
    myCustomManager = custom;
  }

  private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager)manager;
      }
    }
    return null;
  }

  @Override
  public void checkServerTrusted(final X509Certificate[] certificates, String s) throws CertificateException {
    checkServerTrusted(certificates, s, true, true);
  }

  public void checkServerTrusted(final X509Certificate[] certificates, String s, boolean addToKeyStore, boolean askUser)
    throws CertificateException {
    try {
      mySystemManager.checkServerTrusted(certificates, s);
    }
    catch (CertificateException e) {
      // check-then-act sequence
      synchronized (myCustomManager) {
        try {
          myCustomManager.checkServerTrusted(certificates, s);
        }
        catch (CertificateException e2) {
          if (myCustomManager.isBroken() || !confirmAndUpdate(certificates, addToKeyStore, askUser)) {
            throw e;
          }
        }
      }
    }
  }

  private boolean confirmAndUpdate(final X509Certificate[] chain, boolean addToKeyStore, boolean askUser) {
    Application app = ApplicationManager.getApplication();
    final X509Certificate endPoint = chain[0];
    // IDEA-123467 and IDEA-123335 workaround
    String threadClassName = StringUtil.notNullize(Thread.currentThread().getClass().getCanonicalName());
    if (threadClassName.equals("sun.awt.image.ImageFetcher")) {
      LOG.debug("Image Fetcher thread is detected. Certificate check will be skipped.");
      return true;
    }
    if (app.isUnitTestMode() || app.isHeadlessEnvironment() || CertificateManager.getInstance().getState().ACCEPT_AUTOMATICALLY) {
      LOG.debug("Certificate will be accepted automatically");
      if (addToKeyStore) {
        myCustomManager.addCertificate(endPoint);
      }
      return true;
    }
    boolean accepted = askUser && CertificateManager.showAcceptDialog(() -> {
      // TODO may be another kind of warning, if default trust store is missing
      return CertificateWarningDialog.createUntrustedCertificateWarning(endPoint);
    });
    if (accepted) {
      LOG.info("Certificate was accepted by user");
      if (addToKeyStore) {
        myCustomManager.addCertificate(endPoint);
      }
    }
    return accepted;
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return ArrayUtil.mergeArrays(mySystemManager.getAcceptedIssuers(), myCustomManager.getAcceptedIssuers());
  }

  public X509TrustManager getSystemManager() {
    return mySystemManager;
  }

  public MutableTrustManager getCustomManager() {
    return myCustomManager;
  }

  /**
   * Trust manager that supports modifications of underlying physical key store.
   * It can also notify clients about such modifications, see {@link #addListener(CertificateListener)}.
   *
   * @see com.intellij.util.net.ssl.CertificateListener
   */
  public static class MutableTrustManager extends ClientOnlyTrustManager {
    private final String myPath;
    private final String myPassword;
    private final TrustManagerFactory myFactory;
    private final KeyStore myKeyStore;
    private final ReadWriteLock myLock = new ReentrantReadWriteLock();
    private final Lock myReadLock = myLock.readLock();
    private final Lock myWriteLock = myLock.writeLock();
    // reloaded after each modification
    private X509TrustManager myTrustManager;

    private final EventDispatcher<CertificateListener> myDispatcher = EventDispatcher.create(CertificateListener.class);

    private MutableTrustManager(@NotNull String path, @NotNull String password) {
      myPath = path;
      myPassword = password;
      // initialization step
      myWriteLock.lock();
      try {
        myFactory = createFactory();
        myKeyStore = createKeyStore(path, password);
        myTrustManager = initFactoryAndGetManager();
      }
      finally {
        myWriteLock.unlock();
      }
    }

    private static TrustManagerFactory createFactory() {
      try {
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      }
      catch (NoSuchAlgorithmException e) {
        return null;
      }
    }

    private static KeyStore createKeyStore(@NotNull String path, @NotNull String password) {
      KeyStore keyStore;
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
          if (!FileUtil.createParentDirs(cacertsFile)) {
            LOG.error("Cannot create directories: " + cacertsFile.getParent());
            return null;
          }
          keyStore.load(null, password.toCharArray());
        }
      }
      catch (Exception e) {
        LOG.error(e);
        return null;
      }
      return keyStore;
    }


    /**
     * Add certificate to underlying trust store.
     *
     * @param certificate server's certificate
     * @return whether the operation was successful
     */
    public boolean addCertificate(@NotNull X509Certificate certificate) {
      myWriteLock.lock();
      try {
        if (isBroken()) {
          return false;
        }
        myKeyStore.setCertificateEntry(createAlias(certificate), certificate);
        flushKeyStore();
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        myDispatcher.getMulticaster().certificateAdded(certificate);
        return true;
      }
      catch (Exception e) {
        LOG.error("Can't add certificate", e);
        return false;
      }
      finally {
        myWriteLock.unlock();
      }
    }

    /**
     * Add certificate, loaded from file at {@code path}, to underlying trust store.
     *
     * @param path path to file containing certificate
     * @return whether the operation was successful
     */
    public boolean addCertificate(@NotNull String path) {
      X509Certificate certificate = CertificateUtil.loadX509Certificate(path);
      return certificate != null && addCertificate(certificate);
    }

    private static String createAlias(@NotNull X509Certificate certificate) {
      return CertificateUtil.getCommonName(certificate);
    }

    /**
     * Remove certificate from underlying trust store.
     *
     * @param certificate certificate alias
     * @return whether the operation was successful
     */
    public boolean removeCertificate(@NotNull X509Certificate certificate) {
      return removeCertificate(createAlias(certificate));
    }

    /**
     * Remove certificate, specified by its alias, from underlying trust store.
     *
     * @param alias certificate's alias
     * @return true if removal operation was successful and false otherwise
     */
    public boolean removeCertificate(@NotNull String alias) {
      myWriteLock.lock();
      try {
        if (isBroken()) {
          return false;
        }
        // for listeners
        X509Certificate certificate = getCertificate(alias);
        if (certificate == null) {
          LOG.error("No certificate found for alias: " + alias);
          return false;
        }
        myKeyStore.deleteEntry(alias);
        flushKeyStore();
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        myDispatcher.getMulticaster().certificateRemoved(certificate);
        return true;
      }
      catch (Exception e) {
        LOG.error("Can't remove certificate for alias: " + alias, e);
        return false;
      }
      finally {
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
     * Select all available certificates from underlying trust store. Returned list is not supposed to be modified.
     *
     * @return certificates
     */
    public List<X509Certificate> getCertificates() {
      myReadLock.lock();
      try {
        List<X509Certificate> certificates = new ArrayList<>();
        for (String alias : Collections.list(myKeyStore.aliases())) {
          certificates.add(getCertificate(alias));
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

    /**
     * Check that underlying trust store contains certificate with specified alias.
     *
     * @param alias - certificate's alias to be checked
     * @return - whether certificate is in storage
     */
    public boolean containsCertificate(@NotNull String alias) {
      myReadLock.lock();
      try {
        return myKeyStore.containsAlias(alias);
      }
      catch (KeyStoreException e) {
        LOG.error(e);
        return false;
      } finally {
        myReadLock.unlock();
      }
    }

    boolean removeAllCertificates() {
      for (X509Certificate certificate : getCertificates()) {
        if (!removeCertificate(certificate)) {
          return false;
        }
      }
      return true;
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certificates, String s) throws CertificateException {
      myReadLock.lock();
      try {
        if (keyStoreIsEmpty() || isBroken()) {
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
        if (keyStoreIsEmpty() || isBroken()) {
          return NO_CERTIFICATES;
        }
        return myTrustManager.getAcceptedIssuers();
      }
      finally {
        myReadLock.unlock();
      }
    }

    public void addListener(@NotNull CertificateListener listener) {
      myDispatcher.addListener(listener);
    }

    public void removeListener(@NotNull CertificateListener listener) {
      myDispatcher.removeListener(listener);
    }

    // Guarded by caller's lock
    private boolean keyStoreIsEmpty() {
      try {
        return myKeyStore.size() == 0;
      }
      catch (KeyStoreException e) {
        LOG.error(e);
        return true;
      }
    }

    // Guarded by caller's lock
    private X509TrustManager initFactoryAndGetManager() {
      try {
        if (myFactory != null && myKeyStore != null) {
          myFactory.init(myKeyStore);
          return findX509TrustManager(myFactory.getTrustManagers());
        }
      }
      catch (KeyStoreException e) {
        LOG.error(e);
      }
      return null;
    }

    // Guarded by caller's lock
    private boolean isBroken() {
      return myKeyStore == null || myFactory == null || myTrustManager == null;
    }

    private void flushKeyStore() throws Exception {
      FileOutputStream stream = new FileOutputStream(myPath);
      try {
        myKeyStore.store(stream, myPassword.toCharArray());
      }
      finally {
        StreamUtil.closeStream(stream);
      }
    }
  }
}
