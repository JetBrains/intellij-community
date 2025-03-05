// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.net.internal.ProxyMigrationService;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.JavaProxyProperty;
import com.intellij.util.proxy.PropertiesEncryptionSupport;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @deprecated Use {@link ProxySettings}, {@link ProxyAuthentication}, {@link HttpConnectionUtils}, and {@link ProxyUtils} instead.
 * See method deprecation notices for more details.
 * <p/>
 * For removal in version 24.3
 */
@Deprecated(forRemoval = true)
@State(name = "HttpConfigurable",
  category = SettingsCategory.SYSTEM,
  exportable = true,
  storages = @Storage(value = "proxy.settings.xml", roamingType = RoamingType.DISABLED), reportStatistic = false)
public class HttpConfigurable implements PersistentStateComponent<HttpConfigurable>, Disposable {
  private static final Logger LOG = Logger.getInstance(HttpConfigurable.class);
  private static final Path PROXY_CREDENTIALS_FILE = Paths.get(PathManager.getOptionsPath(), "proxy.settings.pwd");

  // only one out of these three should be true
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public boolean USE_HTTP_PROXY;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public boolean USE_PROXY_PAC;
  // USE_NO_PROXY = !USE_HTTP_PROXY && !USE_PROXY_PAC

  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public boolean PROXY_TYPE_IS_SOCKS;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public String PROXY_HOST;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public int PROXY_PORT = 80;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public String PROXY_EXCEPTIONS;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public boolean USE_PAC_URL;
  /** @deprecated use {@link ProxySettings#getProxyConfiguration()} or {@link ProxySettings#setProxyConfiguration(ProxyConfiguration)}  */
  @Deprecated(forRemoval = true) public String PAC_URL;

  /** @deprecated use {@link ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)} or {@link ProxyUtils#setStaticProxyCredentials(ProxySettings, ProxyCredentialStore, Credentials, boolean)} */
  @Deprecated(forRemoval = true) public volatile boolean PROXY_AUTHENTICATION;
  /** @deprecated this flag shouldn't be persisted. In HttpConfigurable it controls whether the password is dropped from the persistence.
   * But if the user wants the password to not be remembered, then such a password should never reach persistence in the first place.
   *
   * @see ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)
   * @see ProxyAuthentication
   */
  @Deprecated(forRemoval = true) public boolean KEEP_PROXY_PASSWORD;

  /** @deprecated without replacement */
  @Deprecated(forRemoval = true) public transient String LAST_ERROR;

  /** @deprecated belongs to UI, for removal without a replacement */
  @Deprecated(forRemoval = true) public transient String CHECK_CONNECTION_URL = "http://";

  /** @deprecated use {@link ProxyAuthentication#isPromptedAuthenticationCancelled(String, int)} with StaticProxy configuration
   * from {@link ProxySettings#getProxyConfiguration()} */
  @Deprecated(forRemoval = true) public transient volatile boolean AUTHENTICATION_CANCELLED;

  private final Map<CommonProxy.HostInfo, ProxyInfo> myGenericPasswords = new HashMap<>();
  private final Set<CommonProxy.HostInfo> myGenericCancelled = new HashSet<>();
  private final transient Object myLock = new Object();

  //private transient IdeaWideProxySelector mySelector;

  // -> drop, unify auth methods, use base64 encoding like it is done for generic auth
  private final transient PropertiesEncryptionSupport myEncryptionSupport = new PropertiesEncryptionSupport(new SecretKeySpec(new byte[] {
    (byte)0x50, (byte)0x72, (byte)0x6f, (byte)0x78, (byte)0x79, (byte)0x20, (byte)0x43, (byte)0x6f,
    (byte)0x6e, (byte)0x66, (byte)0x69, (byte)0x67, (byte)0x20, (byte)0x53, (byte)0x65, (byte)0x63
  }, "AES"));

  // -> drop, see explanation above
  private final transient NotNullLazyValue<Properties> myProxyCredentials = NotNullLazyValue.createValue(() -> {
    try {
      if (!Files.exists(PROXY_CREDENTIALS_FILE)) {
        return new Properties();
      }

      return myEncryptionSupport.load(PROXY_CREDENTIALS_FILE);
    }
    catch (Throwable th) {
      LOG.info(th);
    }
    return new Properties();
  });

  public static HttpConfigurable getInstance() {
    return ApplicationManager.getApplication().getService(HttpConfigurable.class);
  }

  /** @deprecated use {@link ProxyUtils#editConfigurable(ProxySettings, JComponent)} */
  @Deprecated
  public static boolean editConfigurable(@Nullable JComponent parent) {
    return ShowSettingsUtil.getInstance().editConfigurable(parent, new HttpProxyConfigurable());
  }

  @Override
  public HttpConfigurable getState() {
    CommonProxy.isInstalledAssertion();

    HttpConfigurable state = new HttpConfigurable();
    XmlSerializerUtil.copyBean(this, state);
    if (!KEEP_PROXY_PASSWORD) {
      removeSecure("proxy.password");
    }
    correctPasswords(state);
    return state;
  }

  @Override
  public void initializeComponent() {
    if (ProxyMigrationService.getInstance().isNewUser()) { // temporary! will be removed in new proxy settings implementation
      switchDefaultForNewUser();
    }
  }

  /** @deprecated use {@link JdkProxyCustomizer#getOriginalProxySelector()} */
  @Deprecated(forRemoval = true)
  public @NotNull ProxySelector getOnlyBySettingsSelector() {
    return JdkProxyCustomizer.getInstance().getOriginalProxySelector();
  }

  @Override
  public void dispose() { }

  // -> drop, transient auth will be stored separately from persisted auth
  private void correctPasswords(@NotNull HttpConfigurable to) {
    synchronized (myLock) {
      to.myGenericPasswords.values().removeIf(it -> !it.isStore());
    }
  }

  @Override
  public void loadState(@NotNull HttpConfigurable state) {
    XmlSerializerUtil.copyBean(state, this);
    if (!KEEP_PROXY_PASSWORD) {
      removeSecure("proxy.password");
    }
    correctPasswords(this);
  }

  /** @deprecated use {@link ProxyAuthentication#isPromptedAuthenticationCancelled(String, int)} */
  @Deprecated
  public boolean isGenericPasswordCanceled(@NotNull String host, int port) {
    synchronized (myLock) {
      return myGenericCancelled.contains(new CommonProxy.HostInfo(null, host, port));
    }
  }

  @ApiStatus.Internal
  public void removeGenericPasswordCancellation(@NotNull String host, int port) {
    synchronized (myLock) {
      myGenericCancelled.remove(new CommonProxy.HostInfo(null, host, port));
    }
  }

  @ApiStatus.Internal
  public void clearGenericCancellations() {
    synchronized (myLock) {
      myGenericCancelled.clear();
    }
  }

  @ApiStatus.Internal
  public void setGenericPasswordCanceled(final String host, final int port) { // IdeProxyService auth
    synchronized (myLock) {
      myGenericCancelled.add(new CommonProxy.HostInfo(null, host, port));
    }
  }

  /** @deprecated use {@link ProxyCredentialStore#getCredentials(String, int)} */
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getGenericPassword(@NotNull String host, int port) {
    ProxyInfo proxyInfo;
    synchronized (myLock) {
      if (myGenericPasswords.isEmpty()) {
        return null;
      }
      proxyInfo = myGenericPasswords.get(new CommonProxy.HostInfo(null, host, port));
    }
    if (proxyInfo == null) {
      return null;
    }
    return new PasswordAuthentication(proxyInfo.getUsername(), decode(String.valueOf(proxyInfo.getPasswordCrypt())).toCharArray());
  }

  /** @deprecated use {@link ProxyCredentialStore#setCredentials(String, int, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("WeakerAccess")
  public void putGenericPassword(final String host, final int port, @NotNull PasswordAuthentication authentication, boolean remember) {
    PasswordAuthentication coded = new PasswordAuthentication(authentication.getUserName(), encode(String.valueOf(authentication.getPassword())).toCharArray());
    synchronized (myLock) {
      myGenericPasswords.put(new CommonProxy.HostInfo(null, host, port), new ProxyInfo(remember, coded.getUserName(), String.valueOf(coded.getPassword())));
    }
  }

  /** @deprecated use {@link ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)} or {@link ProxyUtils#setStaticProxyCredentials(ProxySettings, ProxyCredentialStore, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  @Transient
  public @Nullable String getProxyLogin() {
    return getSecure("proxy.login");
  }

  /** @deprecated use {@link ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)} or {@link ProxyUtils#setStaticProxyCredentials(ProxySettings, ProxyCredentialStore, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  @Transient
  public void setProxyLogin(String login) {
    storeSecure("proxy.login", login);
  }

  /** @deprecated use {@link ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)} or {@link ProxyUtils#setStaticProxyCredentials(ProxySettings, ProxyCredentialStore, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  @Transient
  public @Nullable String getPlainProxyPassword() {
    return getSecure("proxy.password");
  }

  /** @deprecated use {@link ProxyUtils#getStaticProxyCredentials(ProxySettings, ProxyCredentialProvider)} or {@link ProxyUtils#setStaticProxyCredentials(ProxySettings, ProxyCredentialStore, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  @Transient
  public void setPlainProxyPassword (String password) {
    storeSecure("proxy.password", password);
  }


  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static String encode(String password) {
    return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @deprecated use {@link ProxyAuthentication#getPromptedAuthentication(String, String, int)}.
   * <b>ARGUMENT ORDER HAS BEEN CHANGED!</b>
   * <p/>
   * @param prefix is never used with anything other than "Proxy authentication: "
   * @param remember should be a hint, dropped in new API
   */
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getGenericPromptedAuthentication(final @Nls String prefix, final @NlsSafe String host,
                                                                 final @Nls String prompt, final int port, final boolean remember) {
    Credentials credentials = ProxyAuthentication.getInstance().getPromptedAuthentication(prompt, host, port);
    return credentialsToPasswordAuth(credentials);
  }

  private static PasswordAuthentication credentialsToPasswordAuth(Credentials credentials) {
    if (!CredentialAttributesKt.isFulfilled(credentials)) {
      return null;
    }
    return new PasswordAuthentication(credentials.getUserName(), Objects.requireNonNull(credentials.getPassword()).toCharArray());
  }

  /** @deprecated use {@link ProxyAuthentication#getPromptedAuthentication(String, String, int)} */
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getPromptedAuthentication(final String host, final @Nls String prompt) {
    Credentials credentials = ProxyAuthentication.getInstance().getPromptedAuthentication(prompt, host, PROXY_PORT);
    return credentialsToPasswordAuth(credentials);
  }

  /** @deprecated left for compatibility with com.intellij.openapi.project.impl.IdeaServerSettings */
  @Deprecated(forRemoval = true)
  public void readExternal(Element element) throws InvalidDataException {
    loadState(XmlSerializer.deserialize(element, HttpConfigurable.class));
  }

  /** @deprecated left for compatibility with com.intellij.openapi.project.impl.IdeaServerSettings */
  @Deprecated(forRemoval = true)
  public void writeExternal(Element element) throws WriteExternalException {
    com.intellij.util.xmlb.XmlSerializer.serializeInto(getState(), element);
    if (USE_PROXY_PAC && USE_HTTP_PROXY && !ApplicationManager.getApplication().isDisposed()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
        if (frame != null) {
          USE_PROXY_PAC = false;
          Messages.showMessageDialog(frame.getComponent(), IdeBundle.message("message.text.proxy.both.use.proxy.and.autodetect.proxy.set"),
                                     IdeBundle.message("dialog.title.proxy.setup"), Messages.getWarningIcon());
          editConfigurable(frame.getComponent());
        }
      }, ModalityState.nonModal());
    }
  }

  /**
   * todo [all] It is NOT necessary to call anything if you obey common IDE proxy settings;
   * todo if you want to define your own behaviour, refer to {@link CommonProxy}
   *
   * Also, this method is useful in a way that it tests connection to the host [through proxy].
   *
   * @param url URL for HTTP connection
   *
   * @deprecated use {@link HttpConnectionUtils#prepareUrl(String)}
   */
  @Deprecated(forRemoval = true)
  public void prepareURL(@NotNull String url) throws IOException {
    HttpConnectionUtils.prepareUrl(url);
  }

  /** @deprecated use {@link HttpConnectionUtils#openConnection(String)} */
  @Deprecated(forRemoval = true)
  public @NotNull URLConnection openConnection(@NotNull String location) throws IOException {
    return HttpConnectionUtils.openConnection(location);
  }

  /**
   * Opens HTTP connection to a given location using configured http proxy settings.
   * @param location url to connect to
   * @return instance of {@link HttpURLConnection}
   * @throws IOException in case of any I/O troubles or if created connection isn't instance of HttpURLConnection.
   *
   * @deprecated use {@link HttpConnectionUtils#openHttpConnection(String)}
   */
  @Deprecated(forRemoval = true)
  public @NotNull HttpURLConnection openHttpConnection(@NotNull String location) throws IOException {
    return HttpConnectionUtils.openHttpConnection(location);
  }

  /** @deprecated this method is 1. a utility that shouldn't be a method;
   * 2. error-prone as it only considers the case when proxy is specified statically, i.e., PAC configuration is not considered.
   * Reimplement at use site if necessary.
   * @see ProxyAuthentication
   */
  @Deprecated(forRemoval = true)
  public boolean isHttpProxyEnabledForUrl(@Nullable String url) {
    if (!USE_HTTP_PROXY) return false;
    URI uri = url != null ? VfsUtil.toUri(url) : null;
    return uri == null || !isProxyException(uri.getHost());
  }

  /** @deprecated use {@link ProxyUtils#getApplicableProxiesAsJvmProperties(URI, ProxyCredentialProvider, ProxySelector)}.
   * If autodetection really needs to be disallowed, check {@link ProxySettings} first. Keep in mind that
   * proxy configuration depends on the URI, so it cannot be null. If you only care about statically configured proxies, see
   * {@link ProxyUtils#asJvmProperties(ProxyConfiguration.StaticProxyConfiguration, ProxyCredentialProvider)}.
   * Also, the new util has different properties for user and password that match JDK system properties
   * (see {@link JavaProxyProperty#HTTP_PROXY_USER}). */
  @Deprecated(forRemoval = true)
  public @NotNull List<Pair<String, String>> getJvmProperties(boolean withAutodetection, @Nullable URI uri) {
    if (!USE_HTTP_PROXY && !USE_PROXY_PAC) {
      return Collections.emptyList();
    }

    if (uri != null && isProxyException(uri)) {
      return Collections.emptyList();
    }

    List<Pair<String, String>> result = new ArrayList<>();
    if (USE_HTTP_PROXY) {
      boolean putCredentials = KEEP_PROXY_PASSWORD && StringUtil.isNotEmpty(getProxyLogin());
      if (PROXY_TYPE_IS_SOCKS) {
        result.add(pair(JavaProxyProperty.SOCKS_HOST, PROXY_HOST));
        result.add(pair(JavaProxyProperty.SOCKS_PORT, String.valueOf(PROXY_PORT)));
        if (putCredentials) {
          result.add(pair(JavaProxyProperty.SOCKS_USERNAME, getProxyLogin()));
          result.add(pair(JavaProxyProperty.SOCKS_PASSWORD, getPlainProxyPassword()));
        }
      }
      else {
        result.add(pair(JavaProxyProperty.HTTP_HOST, PROXY_HOST));
        result.add(pair(JavaProxyProperty.HTTP_PORT, String.valueOf(PROXY_PORT)));
        result.add(pair(JavaProxyProperty.HTTPS_HOST, PROXY_HOST));
        result.add(pair(JavaProxyProperty.HTTPS_PORT, String.valueOf(PROXY_PORT)));
        if (putCredentials) {
          result.add(pair(JavaProxyProperty.HTTP_USERNAME, getProxyLogin()));
          result.add(pair(JavaProxyProperty.HTTP_PASSWORD, getPlainProxyPassword()));
        }
      }
    }
    else if (withAutodetection && uri != null) {
      List<Proxy> proxies = CommonProxy.getInstance().select(uri);
      // we will just take the first returned proxy, but we have an option to test connection through each of them,
      // for instance, by calling prepareUrl()
      if (!proxies.isEmpty()) {
        for (Proxy proxy : proxies) {
          if (isRealProxy(proxy)) {
            SocketAddress address = proxy.address();
            if (address instanceof InetSocketAddress inetSocketAddress) {
              if (Proxy.Type.SOCKS.equals(proxy.type())) {
                result.add(pair(JavaProxyProperty.SOCKS_HOST, inetSocketAddress.getHostString()));
                result.add(pair(JavaProxyProperty.SOCKS_PORT, String.valueOf(inetSocketAddress.getPort())));
              }
              else {
                result.add(pair(JavaProxyProperty.HTTP_HOST, inetSocketAddress.getHostString()));
                result.add(pair(JavaProxyProperty.HTTP_PORT, String.valueOf(inetSocketAddress.getPort())));
                result.add(pair(JavaProxyProperty.HTTPS_HOST, inetSocketAddress.getHostString()));
                result.add(pair(JavaProxyProperty.HTTPS_PORT, String.valueOf(inetSocketAddress.getPort())));
              }
            }
          }
        }
      }
    }
    return result;
  }

  /** @deprecated use {@link ProxyConfiguration#buildProxyExceptionsMatcher(String)} */
  @Deprecated(forRemoval = true)
  public boolean isProxyException(URI uri) {
    String uriHost = uri.getHost();
    return isProxyException(uriHost);
  }

  @Contract("null -> false")
  private boolean isProxyException(@Nullable String uriHost) {
    if (StringUtil.isEmptyOrSpaces(uriHost) || StringUtil.isEmptyOrSpaces(PROXY_EXCEPTIONS)) {
      return false;
    }
    return ProxyConfiguration.buildProxyExceptionsMatcher(PROXY_EXCEPTIONS).test(uriHost);
  }

  /** @deprecated use {@link ProxyUtils#isRealProxy(Proxy)} */
  @Deprecated(forRemoval = true)
  public static boolean isRealProxy(@NotNull Proxy proxy) {
    return ProxyUtils.isRealProxy(proxy);
  }

  @ApiStatus.Internal
  public void clearGenericPasswords() {
    synchronized (myLock) {
      myGenericPasswords.clear();
      myGenericCancelled.clear();
    }
  }

  /** @deprecated use {@link ProxyCredentialStore#setCredentials(String, int, Credentials, boolean)} */
  @Deprecated(forRemoval = true)
  public void removeGeneric(@NotNull CommonProxy.HostInfo info) { // IdeAuthenticatorService
    synchronized (myLock) {
      myGenericPasswords.remove(info);
    }
  }

  @ApiStatus.Internal
  public boolean isGenericPasswordRemembered(@NotNull String host, int port) {
    synchronized (myLock) {
      if (myGenericPasswords.isEmpty()) return false;
      var proxyInfo = myGenericPasswords.get(new CommonProxy.HostInfo(null, host, port));
      if (proxyInfo == null) return false;
      return proxyInfo.myStore;
    }
  }

  private static class ProxyInfo {
    public boolean myStore;
    public String myUsername;
    public String myPasswordCrypt;

    @SuppressWarnings("UnusedDeclaration")
    ProxyInfo() { }

    ProxyInfo(boolean store, String username, String passwordCrypt) {
      myStore = store;
      myUsername = username;
      myPasswordCrypt = passwordCrypt;
    }

    public boolean isStore() {
      return myStore;
    }

    public void setStore(boolean store) {
      myStore = store;
    }

    public String getUsername() {
      return myUsername;
    }

    public void setUsername(String username) {
      myUsername = username;
    }

    public String getPasswordCrypt() {
      return myPasswordCrypt;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setPasswordCrypt(String passwordCrypt) {
      myPasswordCrypt = passwordCrypt;
    }
  }

  private String getSecure(String key) {
    try {
      synchronized (myProxyCredentials) {
        final Properties props = myProxyCredentials.getValue();
        return props.getProperty(key, null);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return null;
  }

  private void storeSecure(String key, @Nullable String value) {
    if (value == null) {
      removeSecure(key);
      return;
    }

    try {
      synchronized (myProxyCredentials) {
        final Properties props = myProxyCredentials.getValue();
        props.setProperty(key, value);
        myEncryptionSupport.store(props, "Proxy Credentials", PROXY_CREDENTIALS_FILE);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private void removeSecure(String key) {
    try {
      synchronized (myProxyCredentials) {
        final Properties props = myProxyCredentials.getValue();
        props.remove(key);
        myEncryptionSupport.store(props, "Proxy Credentials", PROXY_CREDENTIALS_FILE);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private void switchDefaultForNewUser() {
    // check that settings are really default, just in case
    if (!USE_HTTP_PROXY && !USE_PROXY_PAC && // == USE_NO_PROXY
        !USE_PAC_URL && StringUtil.isEmpty(PAC_URL)) {
      USE_PROXY_PAC = true;
    }
  }
}