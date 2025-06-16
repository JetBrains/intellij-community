// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DigestUtilKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.DigestUtilKt.sha3_256;

/**
 * The central piece of our SSL support - special kind of trust manager, that asks user to confirm
 * untrusted certificate, e.g. if it wasn't found in system-wide storage.
 * <br><br>
 * Enable FINE (=DEBUG) logging level for categories 'com.intellij.util.net.ssl' and 'org.jetbrains.nativecerts'
 * to get verbose debug logging.
 *
 * @author Mikhail Golubev
 */
public final class ConfirmingTrustManager extends ClientOnlyTrustManager {
  private static final Logger LOG = Logger.getInstance(ConfirmingTrustManager.class);
  private static final X509Certificate[] NO_CERTIFICATES = new X509Certificate[0];

  public final ThreadLocal<@Nullable UntrustedCertificateStrategy> myUntrustedCertificateStrategy = ThreadLocal.withInitial(() -> null);

  public static ConfirmingTrustManager createForStorage(@NotNull String path, @NotNull String password) {
    return new ConfirmingTrustManager(getSystemTrustManagers(), new MutableTrustManager(path, password));
  }

  private static @NotNull List<X509TrustManager> getSystemTrustManagers() {
    List<X509TrustManager> result = new ArrayList<>();

    X509TrustManager osManager = getOperatingSystemTrustManager();
    if (osManager != null) {
      result.add(osManager);
    }

    X509TrustManager javaRuntimeManager = getJavaRuntimeDefaultTrustManager();
    if (javaRuntimeManager != null) {
      result.add(javaRuntimeManager);
    }

    return result;
  }

  private static @Nullable X509TrustManager getJavaRuntimeDefaultTrustManager() {
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
      LOG.error("Cannot get default JVM trust store", e);
    }
    return null;
  }

  private static @Nullable X509TrustManager getOperatingSystemTrustManager() {
    try {
      Collection<X509Certificate> additionalTrustedCertificates =
        OsCertificatesService.getInstance().getCustomOsSpecificTrustedCertificates();
      if (additionalTrustedCertificates.isEmpty()) {
        // don't nag developers, on jetbrains developer's machine this list is usually empty on MacOs and Windows
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.warn(
            "Received an empty list of custom trusted root certificates from the system. Check log above for possible errors, enable debug logging in category 'org.jetbrains.nativecerts' for more information");
        }
        return null;
      }

      X509TrustManager x509TrustManager = createTrustManagerFromCertificates(additionalTrustedCertificates);

      List<String> acceptedRoots =
        Arrays.stream(x509TrustManager.getAcceptedIssuers())
        .map(certificate -> certificate.getSubjectX500Principal().toString())
        .sorted()
        .toList();
      LOG.debug("Accepted trusted certificate roots from the system: \n" + StringUtil.join(acceptedRoots, "\n"));

      return x509TrustManager;
    }
    catch (Throwable exception) {
      LOG.error("Unable to build system trusted certificates manager, only JVM-bundled roots will be used: " + exception.getMessage(), exception);
      return null;
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public static @NotNull X509TrustManager createTrustManagerFromCertificates(@NotNull Collection<? extends X509Certificate> certificates) throws Exception {
    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    for (X509Certificate certificate : certificates) {
      ks.setCertificateEntry(
        certificate.getSubjectX500Principal().toString() + "-" +
        DigestUtilKt.hashToHexString(certificate.getEncoded(), sha3_256()), certificate);
    }

    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);
    List<X509TrustManager> x509TrustManagers = ContainerUtil.filterIsInstance(tmf.getTrustManagers(), X509TrustManager.class);
    if (x509TrustManagers.isEmpty()) {
      throw new IllegalStateException("Unable to create X509TrustManager from keystore: no X509TrustManager instances returned, only " +
                                      Strings.join(Arrays.asList(tmf.getTrustManagers()), " "));
    }
    if (x509TrustManagers.size() > 1) {
      throw new IllegalStateException(
        "Unable to create X509TrustManager from keystore: more than one X509TrustManager instance returned: " +
        Strings.join(x509TrustManagers, " "));
    }

    return x509TrustManagers.get(0);
  }

  private final List<X509TrustManager> mySystemManagers;
  private final MutableTrustManager myCustomManager;

  private ConfirmingTrustManager(List<X509TrustManager> system, MutableTrustManager custom) {
    mySystemManagers = system;
    myCustomManager = custom;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void addSystemTrustManager(X509TrustManager manager) {
    mySystemManagers.add(manager);
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void removeSystemTrustManager(X509TrustManager manager) {
    if (!mySystemManagers.remove(manager)) {
      throw new IllegalArgumentException("trust manager was not in the list of system trust managers: " + manager);
    }
  }

  private static @Nullable X509TrustManager findX509TrustManager(TrustManager[] managers) {
    for (TrustManager manager : managers) {
      if (manager instanceof X509TrustManager) {
        return (X509TrustManager)manager;
      }
    }
    return null;
  }

  @Override
  public void checkServerTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
    checkServerTrusted(chain, authType, (String)null);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
    String remoteHost;
    SocketAddress sa = socket.getRemoteSocketAddress();
    if (sa instanceof InetSocketAddress isa) {
      remoteHost = isa.getHostString() + ":" + isa.getPort();
    }
    else {
      remoteHost = sa.toString();
    }
    checkServerTrusted(chain, authType, remoteHost);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
    String remoteHost = engine.getPeerHost();
    int peerPort = engine.getPeerPort();
    if (peerPort > 0) {
      remoteHost = remoteHost + ":" + peerPort;
    }
    checkServerTrusted(chain, authType, remoteHost);
  }

  private void checkServerTrusted(X509Certificate[] chain, String authType, String remoteHost) throws CertificateException {
    withCalculatedCertificateStrategy(strategyWithReason -> {
      boolean askUser = strategyWithReason.getStrategy() == UntrustedCertificateStrategy.ASK_USER;
      String askUserReason = strategyWithReason.getReason();
      checkServerTrusted(chain, authType, remoteHost, new CertificateConfirmationParameters(askUser, true, null, null, askUserReason));
    });
  }

  private void withCalculatedCertificateStrategy(ThrowableConsumer<? super UntrustedCertificateStrategyWithReason, ? extends CertificateException> block) throws CertificateException {
    UntrustedCertificateStrategy initialStrategy = myUntrustedCertificateStrategy.get();
    if (initialStrategy != null) {
      block.consume(new UntrustedCertificateStrategyWithReason(initialStrategy, null));
    }
    else {
      UntrustedCertificateStrategyWithReason strategy = ApplicationManager.getApplication().getService(InitialUntrustedCertificateStrategyProvider.class).getStrategy();
      block.consume(strategy);
    }
  }

  /**
   * @deprecated use an overload with {@link CertificateConfirmationParameters}.
   */
  @Deprecated
  public void checkServerTrusted(final X509Certificate[] chain, String authType, boolean addToKeyStore, boolean askUser)
    throws CertificateException {
    checkServerTrusted(chain, authType, null, new CertificateConfirmationParameters(askUser, addToKeyStore, null, null, null));
  }

  public void checkServerTrusted(final X509Certificate[] chain, String authType, @NotNull CertificateConfirmationParameters parameters) throws CertificateException {
    checkServerTrusted(chain, authType, null, parameters);
  }

  private void checkServerTrusted(final X509Certificate[] chain, String authType, String remoteHost, @NotNull CertificateConfirmationParameters parameters)
    throws CertificateException {

    CertificateException lastCertificateException = null;
    for (X509TrustManager trustManager : mySystemManagers) {
      try {
        trustManager.checkServerTrusted(chain, authType);
        return;
      }
      catch (CertificateException e) {
        // Check next or fall-back to custom manager
        lastCertificateException = e;
      }
    }

    // check-then-act sequence
    synchronized (myCustomManager) {
      try {
        myCustomManager.checkServerTrusted(chain, authType);
      }
      catch (CertificateException e) {
        if (myCustomManager.isBroken() || !confirmAndUpdate(chain, remoteHost, parameters, authType)) {
          throw lastCertificateException != null ? lastCertificateException : e;
        }
      }
    }
  }

  private boolean confirmAndUpdate(final X509Certificate[] chain, String remoteHost, @NotNull CertificateConfirmationParameters parameters, String authType) {
    Application app = ApplicationManager.getApplication();
    final X509Certificate endPoint = chain[0];
    // IDEA-123467 and IDEA-123335 workaround
    String threadClassName = Strings.notNullize(Thread.currentThread().getClass().getCanonicalName());
    if (threadClassName.equals("sun.awt.image.ImageFetcher")) {
      LOG.debug("Image Fetcher thread is detected. Certificate check will be skipped.");
      return true;
    }

    if (app.isHeadlessEnvironment() || CertificateManager.getInstance().getState().ACCEPT_AUTOMATICALLY) {
      LOG.debug("Certificate will be accepted automatically");
      if (parameters.myAddToKeyStore) {
        myCustomManager.addCertificate(endPoint);
      }
      return true;
    }

    if (app.isUnitTestMode()) {
      return false;
    }

    boolean accepted = false;
    CertificateProvider certificateProvider = new CertificateProvider();
    if (parameters.myAskUser) {
      String acceptLogMessage = "Going to ask user about certificate for: " + endPoint.getSubjectX500Principal().toString() +
                       ", issuer: " + endPoint.getIssuerX500Principal().toString();
      if (parameters.myAskOrRejectReason != null) {
        acceptLogMessage += ". Reason: " + parameters.myAskOrRejectReason;
      }
      LOG.info(acceptLogMessage);
      CertificateWarningDialogProvider dialogProvider = CertificateWarningDialogProvider.Companion.getInstance();
      if (dialogProvider == null) {
        LOG.warn("Accepting dialog wasn't shown, because DialogProvider in unavailable now");
      } else {
        accepted = CertificateManager.Companion.showAcceptDialog(() -> {
          // TODO may be another kind of warning, if default trust store is missing
          return dialogProvider.createCertificateWarningDialog(Arrays.stream(chain).toList(), myCustomManager, remoteHost, authType, certificateProvider);
        });
      }
    }
    else {
      String rejectLogMessage = "Didn't show certificate dialog for: " + endPoint.getSubjectX500Principal().toString() +
                       ", issuer: " + endPoint.getIssuerX500Principal().toString();
      if (parameters.myAskOrRejectReason != null) {
        rejectLogMessage += ". Reason: " + parameters.myAskOrRejectReason;
      }
      LOG.warn(rejectLogMessage);
    }
    if (accepted) {
      LOG.info("Certificate was accepted by user");
      if (parameters.myAddToKeyStore) {
        if (certificateProvider.getSelectedCertificate() == null) {
          LOG.warn("Certificate wasn't selected, but accepted");
          accepted = false;
        } else {
          myCustomManager.addCertificate(certificateProvider.getSelectedCertificate());
        }
      }
      if (certificateProvider.isChainRemainUnsafe()) {
        LOG.info("The certificate chain remains untrusted. The request execution will not proceed");
        accepted = false;
      }
      if (parameters.myOnUserAcceptCallback != null) {
        parameters.myOnUserAcceptCallback.run();
      }
    }
    return accepted;
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    Set<X509Certificate> certificates = new HashSet<>();

    for (X509TrustManager manager : mySystemManagers) {
      try {
        certificates.addAll(Arrays.asList(manager.getAcceptedIssuers()));
      }
      catch (Throwable exception) {
        LOG.error("Could not get list of accepted issuers (trusted root identities) from " +
                  manager.toString() + " (" + manager.getClass().getName() + ")", exception);
      }
    }

    certificates.addAll(Arrays.asList(myCustomManager.getAcceptedIssuers()));

    return certificates.toArray(X509Certificate[]::new);
  }

  public MutableTrustManager getCustomManager() {
    return myCustomManager;
  }

  /**
   * Trust manager that supports modifications of underlying physical key store.
   * It can also notify clients about such modifications, see {@link #addListener(CertificateListener)}.
   *
   * @see CertificateListener
   */
  public static final class MutableTrustManager extends ClientOnlyTrustManager {
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
        LOG.error("Cannot create trust manager factory", e);
        return null;
      }
    }

    private static KeyStore createKeyStore(@NotNull String path, @NotNull String password) {
      KeyStore keyStore;
      try {
        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        Path cacertsFile = Path.of(path);
        if (Files.exists(cacertsFile)) {
          try (InputStream stream = Files.newInputStream(cacertsFile)) {
            keyStore.load(stream, password.toCharArray());
          }
        }
        else {
          try {
            Files.createDirectories(cacertsFile.getParent());
          }
          catch (IOException e) {
            LOG.error("Cannot create directories: " + cacertsFile.getParent(), e);
            return null;
          }

          keyStore.load(null, password.toCharArray());
        }
      }
      catch (Exception e) {
        LOG.error("Cannot create key store", e);
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
        LOG.info("Added certificate for '" + certificate.getSubjectX500Principal().toString() + "' to " + myPath);
        // trust manager should be updated each time its key store was modified
        myTrustManager = initFactoryAndGetManager();
        myDispatcher.getMulticaster().certificateAdded(certificate);
        return true;
      }
      catch (Exception e) {
        LOG.error("Cannot add certificate", e);
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
        LOG.error("Cannot remove certificate for alias: " + alias, e);
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
    public @Nullable X509Certificate getCertificate(@NotNull String alias) {
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
    
    public List<String> getAliases() {
      myReadLock.lock();
      try {
        return Collections.list(myKeyStore.aliases());
      }
      catch (KeyStoreException e) {
        return Collections.emptyList();
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
        return List.copyOf(certificates);
      }
      catch (Exception e) {
        LOG.error(e);
        return Collections.emptyList();
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
      }
      finally {
        myReadLock.unlock();
      }
    }

    @VisibleForTesting
    @ApiStatus.Internal
    public boolean removeAllCertificates() {
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
          final TrustManager[] trustManagers = myFactory.getTrustManagers();
          final X509TrustManager result = findX509TrustManager(trustManagers);
          if (result == null) {
            LOG.error("Cannot find X509 trust manager among " + Arrays.toString(trustManagers));
          }
          return result;
        }
      }
      catch (KeyStoreException e) {
        LOG.error("Cannot initialize trust store", e);
      }
      return null;
    }

    // Guarded by caller's lock
    private boolean isBroken() {
      return myKeyStore == null || myFactory == null || myTrustManager == null;
    }

    private void flushKeyStore() throws Exception {
      try (FileOutputStream stream = new FileOutputStream(myPath)) {
        myKeyStore.store(stream, myPassword.toCharArray());
      }
    }
  }

  public static final class CertificateConfirmationParameters {
    private final boolean myAskUser;
    private final @Nullable String myAskOrRejectReason;
    private final boolean myAddToKeyStore;
    private final @Nullable @NlsContexts.DialogMessage String myCertificateDetails;
    private final @Nullable Runnable myOnUserAcceptCallback;

    /**
     * Ask for confirmation from the user.
     *
     * @param addToKeyStore if true, then add the certificate to the key store. Otherwise, the user will be able to accept it for this
     *                     request only.
     * @param certificateDetails additional details to be presented in the certificate acceptance dialog.
     * @param onUserAcceptCallback a custom callback that will be called after the user has accepted the certificate.
     */
    @SuppressWarnings("unused") // part of the public API
    public static @NotNull CertificateConfirmationParameters askConfirmation(boolean addToKeyStore,
                                                                             @Nullable @NlsContexts.DialogMessage String certificateDetails,
                                                                             @Nullable Runnable onUserAcceptCallback) {
      return new CertificateConfirmationParameters(true, addToKeyStore, certificateDetails, onUserAcceptCallback, null);
    }

    /**
     * Forbids to ask the confirmation from the user.
     * <p>
     * Such a certificate may only be accepted in the unit test mode or if {@link CertificateManager.Config#ACCEPT_AUTOMATICALLY} is set to
     * true.
     */
    public static @NotNull CertificateConfirmationParameters doNotAskConfirmation() {
      return new CertificateConfirmationParameters(false, false, null, null, null);
    }

    private CertificateConfirmationParameters(boolean askUser,
                                              boolean addToKeyStore,
                                              @Nullable @NlsContexts.DialogMessage String certificateDetails,
                                              @Nullable Runnable onUserAcceptCallback,
                                              @Nullable String askOrRejectReason) {
      myAskUser = askUser;
      myAddToKeyStore = addToKeyStore;
      myCertificateDetails = certificateDetails;
      myOnUserAcceptCallback = onUserAcceptCallback;
      myAskOrRejectReason = askOrRejectReason;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CertificateConfirmationParameters that = (CertificateConfirmationParameters)o;
      return myAskUser == that.myAskUser &&
             myAddToKeyStore == that.myAddToKeyStore &&
             Objects.equals(myCertificateDetails, that.myCertificateDetails) &&
             Objects.equals(myOnUserAcceptCallback, that.myOnUserAcceptCallback);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myAskUser, myAddToKeyStore, myCertificateDetails, myOnUserAcceptCallback);
    }
  }

}
