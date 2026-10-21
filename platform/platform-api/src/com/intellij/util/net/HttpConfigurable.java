// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.net.internal.ProxyMigrationService;
import com.intellij.util.proxy.PropertiesEncryptionSupport;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.spec.SecretKeySpec;
import javax.swing.JComponent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/// @deprecated Use [ProxySettings], [ProxyAuthentication], [HttpConnectionUtils], and [ProxyUtils] instead.
/// See method deprecation notices for more details.
@Deprecated(forRemoval = true)
@State(
  name = "HttpConfigurable",
  category = SettingsCategory.SYSTEM,
  exportable = true,
  storages = @Storage(value = "proxy.settings.xml", roamingType = RoamingType.DISABLED),
  reportStatistic = false
)
@SuppressWarnings({"unused", "DeprecatedIsStillUsed", "removal", "UnnecessaryFullyQualifiedName"})
public class HttpConfigurable implements PersistentStateComponent<HttpConfigurable>, Disposable {
  private static final Logger LOG = Logger.getInstance(HttpConfigurable.class);
  private static final Path PROXY_CREDENTIALS_FILE = PathManager.getOptionsDir().resolve("proxy.settings.pwd");

  // only one out of these three should be true
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public boolean USE_HTTP_PROXY;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public boolean USE_PROXY_PAC;
  // USE_NO_PROXY = !USE_HTTP_PROXY && !USE_PROXY_PAC

  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public boolean PROXY_TYPE_IS_SOCKS;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public String PROXY_HOST;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public int PROXY_PORT = 80;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public String PROXY_EXCEPTIONS;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public boolean USE_PAC_URL;
  /// @deprecated use [ProxySettings#getProxyConfiguration()] or [ProxySettings#setProxyConfiguration(ProxyConfiguration)]
  @Deprecated(forRemoval = true) public String PAC_URL;

  /// @deprecated the flag is not used in the [ProxySettings] API;
  /// if no authentication is needed, set credentials to `null` via [ProxyCredentialStore#setCredentials(String, int, Credentials, boolean)]
  @Deprecated(forRemoval = true) public volatile boolean PROXY_AUTHENTICATION;

  /// @deprecated this flag shouldn't be persisted. In HttpConfigurable it controls whether the password is dropped from the persistence.
  /// But if the user wants the password to not be remembered, then such a password should never reach persistence in the first place.
  ///
  /// @see ProxyAuthentication
  @Deprecated(forRemoval = true) public boolean KEEP_PROXY_PASSWORD;

  /// @deprecated without replacement
  @Deprecated(forRemoval = true) public transient String LAST_ERROR;

  /// @deprecated use [ProxyAuthentication#isPromptedAuthenticationCancelled(String, int)] with StaticProxy configuration
  /// from [ProxySettings#getProxyConfiguration()]
  @Deprecated(forRemoval = true) public transient volatile boolean AUTHENTICATION_CANCELLED;

  private final Set<HostInfo> myGenericCancelled = new HashSet<>();

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

  /// @deprecated use [HttpProxyConfigurable#editConfigurable(JComponent)]
  @Deprecated
  public static boolean editConfigurable(@Nullable JComponent parent) {
    return HttpProxyConfigurable.editConfigurable(parent);
  }

  @Override
  public @NotNull HttpConfigurable getState() {
    com.intellij.util.proxy.CommonProxy.isInstalledAssertion();

    HttpConfigurable state = new HttpConfigurable();
    XmlSerializerUtil.copyBean(this, state);
    if (!KEEP_PROXY_PASSWORD) removeSecure();
    return state;
  }

  @Override
  public void initializeComponent() {
    if (ProxyMigrationService.getInstance().isNewUser()) { // temporary! will be removed in new proxy settings implementation
      switchDefaultForNewUser();
    }
  }

  /// @deprecated use [JdkProxyCustomizer#getOriginalProxySelector()]
  @Deprecated(forRemoval = true)
  public @NotNull ProxySelector getOnlyBySettingsSelector() {
    return JdkProxyCustomizer.getInstance().getOriginalProxySelector();
  }

  @Override
  public void dispose() { }

  @Override
  public void loadState(@NotNull HttpConfigurable state) {
    XmlSerializerUtil.copyBean(state, this);
    if (!KEEP_PROXY_PASSWORD) removeSecure();
  }

  /// @deprecated use [ProxyAuthentication#isPromptedAuthenticationCancelled(String, int)]
  @Deprecated
  public boolean isGenericPasswordCanceled(@NotNull String host, int port) {
    synchronized (myGenericCancelled) {
      return myGenericCancelled.contains(new HostInfo(host, port));
    }
  }

  @ApiStatus.Internal
  public void removeGenericPasswordCancellation(@NotNull String host, int port) {
    synchronized (myGenericCancelled) {
      myGenericCancelled.remove(new HostInfo(host, port));
    }
  }

  @ApiStatus.Internal
  public void clearGenericCancellations() {
    synchronized (myGenericCancelled) {
      myGenericCancelled.clear();
    }
  }

  @ApiStatus.Internal
  public void setGenericPasswordCanceled(String host, int port) { // IdeProxyService auth
    synchronized (myGenericCancelled) {
      myGenericCancelled.add(new HostInfo(host, port));
    }
  }

  /// @deprecated use [ProxyCredentialStore#getCredentials]
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getGenericPassword(@NotNull String host, int port) {
    LOG.error("deprecated; use `ProxyCredentialStore#getCredentials` instead");
    var credentials = ProxyCredentialStore.getInstance().getCredentials(host, port);
    if (credentials != null) {
      var password = credentials.getPassword();
      return new PasswordAuthentication(credentials.getUserName(), password != null ? password.toCharArray() : new char[0]);
    }
    return null;
  }

  /// @deprecated use [ProxyCredentialStore#getCredentials] instead
  @Deprecated(forRemoval = true)
  @Transient
  public @Nullable String getProxyLogin() {
    LOG.error("deprecated; use `ProxyCredentialStore#getCredentials` instead");
    var credentials = readCredentials();
    return credentials != null ? credentials.getUserName() : null;
  }

  /// @deprecated use [ProxyCredentialStore#getCredentials] instead
  @Deprecated(forRemoval = true)
  @Transient
  public @Nullable String getPlainProxyPassword() {
    LOG.error("deprecated; use `ProxyCredentialStore#getCredentials` instead");
    var credentials = readCredentials();
    return credentials != null ? credentials.getPasswordAsString() : null;
  }

  /// @deprecated use [ProxyCredentialStore#getCredentials] instead
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public @Nullable Credentials readCredentials() {
    try {
      synchronized (myProxyCredentials) {
        var props = myProxyCredentials.getValue();
        var login = props.getProperty("proxy.login", null);
        var password = props.getProperty("proxy.password", null);
        if (login != null && !login.isEmpty()) {
          return new Credentials(login, password == null || password.isEmpty() ? null : password);
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return null;
  }

  /// @deprecated use [ProxyCredentialStore#setCredentials] instead
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public void writeCredentials(@Nullable Credentials credentials) {
    try {
      synchronized (myProxyCredentials) {
        var properties = myProxyCredentials.getValue();
        if (credentials != null && credentials.getUserName() != null) {
          properties.setProperty("proxy.login", credentials.getUserName());
          var password = credentials.getPasswordAsString();
          if (password == null || password.isEmpty()) {
            properties.remove("proxy.password");
          }
          else {
            properties.setProperty("proxy.password", password);
          }
        }
        else {
          properties.remove("proxy.login");
          properties.remove("proxy.password");
        }
        myEncryptionSupport.store(properties, "Proxy Credentials", PROXY_CREDENTIALS_FILE);
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
  }

  private static String decode(String value) {
    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private static String encode(String password) {
    return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
  }

  /// @deprecated use [ProxyAuthentication#getPromptedAuthentication(String, String, int)].
  /// **ARGUMENT ORDER HAS BEEN CHANGED!**
  ///
  /// @param prefix is never used with anything other than "Proxy authentication: "
  /// @param remember should be a hint, dropped in new API
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getGenericPromptedAuthentication(@Nls String prefix, @NlsSafe String host, @Nls String prompt, int port, boolean remember) {
    Credentials credentials = ProxyAuthentication.getInstance().getPromptedAuthentication(prompt, host, port);
    return credentialsToPasswordAuth(credentials);
  }

  private static PasswordAuthentication credentialsToPasswordAuth(Credentials credentials) {
    if (!CredentialAttributesKt.isFulfilled(credentials)) {
      return null;
    }
    return new PasswordAuthentication(credentials.getUserName(), Objects.requireNonNull(credentials.getPassword()).toCharArray());
  }

  /// @deprecated use [ProxyAuthentication#getPromptedAuthentication(String, String, int)]
  @Deprecated(forRemoval = true)
  public PasswordAuthentication getPromptedAuthentication(final String host, final @Nls String prompt) {
    Credentials credentials = ProxyAuthentication.getInstance().getPromptedAuthentication(prompt, host, PROXY_PORT);
    return credentialsToPasswordAuth(credentials);
  }

  /// @deprecated use [HttpConnectionUtils#prepareUrl(String)]
  @Deprecated(forRemoval = true)
  public void prepareURL(@NotNull String url) throws IOException {
    HttpConnectionUtils.prepareUrl(url);
  }

  /// @deprecated use [HttpConnectionUtils#openConnection(String)]
  @Deprecated(forRemoval = true)
  public @NotNull URLConnection openConnection(@NotNull String location) throws IOException {
    return HttpConnectionUtils.openConnection(location);
  }

  /// @deprecated use [HttpConnectionUtils#openHttpConnection(String)]
  @Deprecated(forRemoval = true)
  public @NotNull HttpURLConnection openHttpConnection(@NotNull String location) throws IOException {
    return HttpConnectionUtils.openHttpConnection(location);
  }

  /// @deprecated this method is 1. a utility that shouldn't be a method;
  /// 2. error-prone as it only considers the case when proxy is specified statically, i.e., PAC configuration is not considered.
  /// Reimplement at use site if necessary.
  /// @see ProxyAuthentication
  @Deprecated(forRemoval = true)
  public boolean isHttpProxyEnabledForUrl(@Nullable String url) {
    if (!USE_HTTP_PROXY) return false;
    URI uri = url != null ? VfsUtil.toUri(url) : null;
    return uri == null || !isProxyException(uri.getHost());
  }

  /// @deprecated use [ProxyConfiguration#buildProxyExceptionsMatcher(String)]
  @Deprecated(forRemoval = true)
  public boolean isProxyException(URI uri) {
    return isProxyException(uri.getHost());
  }

  @Contract("null -> false")
  private boolean isProxyException(@Nullable String uriHost) {
    if (StringUtil.isEmptyOrSpaces(uriHost) || StringUtil.isEmptyOrSpaces(PROXY_EXCEPTIONS)) {
      return false;
    }
    return ProxyConfiguration.buildProxyExceptionsMatcher(PROXY_EXCEPTIONS).test(uriHost);
  }

  /// @deprecated use [ProxyUtils#isRealProxy(Proxy)]
  @Deprecated(forRemoval = true)
  public static boolean isRealProxy(@NotNull Proxy proxy) {
    return ProxyUtils.isRealProxy(proxy);
  }

  private void removeSecure() {
    try {
      synchronized (myProxyCredentials) {
        var properties = myProxyCredentials.getValue();
        properties.remove("proxy.password");
        myEncryptionSupport.store(properties, "Proxy Credentials", PROXY_CREDENTIALS_FILE);
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

  private record HostInfo(String host, int port) { }
}
