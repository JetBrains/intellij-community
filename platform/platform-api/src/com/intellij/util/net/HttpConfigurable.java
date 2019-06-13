// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.JavaProxyProperty;
import com.intellij.util.proxy.PropertiesEncryptionSupport;
import com.intellij.util.proxy.SharedProxyConfig;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectObjectProcedure;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;

@State(name = "HttpConfigurable", storages = @Storage("proxy.settings.xml"))
public class HttpConfigurable implements PersistentStateComponent<HttpConfigurable>, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.net.HttpConfigurable");
  private static final File PROXY_CREDENTIALS_FILE = new File(PathManager.getOptionsPath(), "proxy.settings.pwd");
  public static final int CONNECTION_TIMEOUT = HttpRequests.CONNECTION_TIMEOUT;
  public static final int READ_TIMEOUT = HttpRequests.READ_TIMEOUT;
  public static final int REDIRECT_LIMIT = HttpRequests.REDIRECT_LIMIT;

  public boolean PROXY_TYPE_IS_SOCKS;
  public boolean USE_HTTP_PROXY;
  public boolean USE_PROXY_PAC;
  public volatile transient boolean AUTHENTICATION_CANCELLED;
  public String PROXY_HOST;
  public int PROXY_PORT = 80;

  public volatile boolean PROXY_AUTHENTICATION;
  public boolean KEEP_PROXY_PASSWORD;
  public transient String LAST_ERROR;

  private final THashMap<CommonProxy.HostInfo, ProxyInfo> myGenericPasswords = new THashMap<>();
  private final Set<CommonProxy.HostInfo> myGenericCancelled = new THashSet<>();

  public String PROXY_EXCEPTIONS;
  public boolean USE_PAC_URL;
  public String PAC_URL;

  private transient IdeaWideProxySelector mySelector;
  private transient final Object myLock = new Object();

  private transient final PropertiesEncryptionSupport myEncryptionSupport = new PropertiesEncryptionSupport(new SecretKeySpec(new byte[] {
    (byte)0x50, (byte)0x72, (byte)0x6f, (byte)0x78, (byte)0x79, (byte)0x20, (byte)0x43, (byte)0x6f,
    (byte)0x6e, (byte)0x66, (byte)0x69, (byte)0x67, (byte)0x20, (byte)0x53, (byte)0x65, (byte)0x63
  }, "AES"));
  private transient final NotNullLazyValue<Properties> myProxyCredentials = NotNullLazyValue.createValue(() -> {
    try {
      return myEncryptionSupport.load(PROXY_CREDENTIALS_FILE);
    }
    catch (FileNotFoundException ignored) {
    }
    catch (Throwable th) {
      LOG.info(th);
    }
    return new Properties();
  });

  @SuppressWarnings("UnusedDeclaration")
  public transient Getter<PasswordAuthentication> myTestAuthRunnable = new StaticGetter<>(null);
  public transient Getter<PasswordAuthentication> myTestGenericAuthRunnable = new StaticGetter<>(null);

  public static HttpConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(HttpConfigurable.class);
  }

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
    final HttpConfigurable currentState = getState();
    if (currentState != null) {
      final Element serialized = XmlSerializer.serialize(currentState);
      if (serialized == null) {
        // all settings are defaults
        // trying user's proxy configuration entered while obtaining the license
        final SharedProxyConfig.ProxyParameters cfg = SharedProxyConfig.load();
        if (cfg != null) {
          SharedProxyConfig.clear();
          if (cfg.host != null) {
            USE_HTTP_PROXY = true;
            PROXY_HOST = cfg.host;
            PROXY_PORT = cfg.port;
            if (cfg.login != null) {
              setPlainProxyPassword(new String(cfg.password));
              storeSecure("proxy.login", cfg.login);
              PROXY_AUTHENTICATION = true;
              KEEP_PROXY_PASSWORD = true;
            }
          }
        }
      }
    }

    mySelector = new IdeaWideProxySelector(this);
    String name = getClass().getName();
    CommonProxy.getInstance().setCustom(name, mySelector);
    CommonProxy.getInstance().setCustomAuth(name, new IdeaWideAuthenticator(this));
  }

  /**
   * @deprecated use {@link #initializeComponent()}
   */
  @Deprecated
  public void initComponent() {
    initializeComponent();
  }

  @NotNull
  public ProxySelector getOnlyBySettingsSelector() {
    return mySelector;
  }

  @Override
  public void dispose() {
    final String name = getClass().getName();
    CommonProxy.getInstance().removeCustom(name);
    CommonProxy.getInstance().removeCustomAuth(name);
  }

  private void correctPasswords(@NotNull HttpConfigurable to) {
    synchronized (myLock) {
      to.myGenericPasswords.retainEntries(new TObjectObjectProcedure<CommonProxy.HostInfo, ProxyInfo>() {
        @Override
        public boolean execute(CommonProxy.HostInfo hostInfo, ProxyInfo proxyInfo) {
          return proxyInfo.isStore();
        }
      });
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

  public boolean isGenericPasswordCanceled(@NotNull String host, int port) {
    synchronized (myLock) {
      return myGenericCancelled.contains(new CommonProxy.HostInfo(null, host, port));
    }
  }

  public void setGenericPasswordCanceled(final String host, final int port) {
    synchronized (myLock) {
      myGenericCancelled.add(new CommonProxy.HostInfo(null, host, port));
    }
  }

  public PasswordAuthentication getGenericPassword(@NotNull String host, int port) {
    final ProxyInfo proxyInfo;
    synchronized (myLock) {
      proxyInfo = myGenericPasswords.get(new CommonProxy.HostInfo(null, host, port));
    }
    if (proxyInfo == null) {
      return null;
    }
    return new PasswordAuthentication(proxyInfo.getUsername(), decode(String.valueOf(proxyInfo.getPasswordCrypt())).toCharArray());
  }

  public void putGenericPassword(final String host, final int port, @NotNull PasswordAuthentication authentication, boolean remember) {
    PasswordAuthentication coded = new PasswordAuthentication(authentication.getUserName(), encode(String.valueOf(authentication.getPassword())).toCharArray());
    synchronized (myLock) {
      myGenericPasswords.put(new CommonProxy.HostInfo(null, host, port), new ProxyInfo(remember, coded.getUserName(), String.valueOf(coded.getPassword())));
    }
  }

  @Transient
  @Nullable
  public String getProxyLogin() {
    return getSecure("proxy.login");
  }

  @Transient
  public void setProxyLogin(String login) {
    storeSecure("proxy.login", login);
  }

  @Transient
  @Nullable
  public String getPlainProxyPassword() {
    return getSecure("proxy.password");
  }

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

  public PasswordAuthentication getGenericPromptedAuthentication(final String prefix, final String host, final String prompt, final int port, final boolean remember) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myTestGenericAuthRunnable.get();
    }

    final Ref<PasswordAuthentication> value = Ref.create();
    runAboveAll(() -> {
      if (isGenericPasswordCanceled(host, port)) {
        return;
      }

      PasswordAuthentication password = getGenericPassword(host, port);
      if (password != null) {
        value.set(password);
        return;
      }

      AuthenticationDialog dialog = new AuthenticationDialog(PopupUtil.getActiveComponent(), prefix + host,
                                                             "Please enter credentials for: " + prompt, "", "", remember);
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        AuthenticationPanel panel = dialog.getPanel();
        PasswordAuthentication passwordAuthentication = new PasswordAuthentication(panel.getLogin(), panel.getPassword());
        putGenericPassword(host, port, passwordAuthentication, remember && panel.isRememberPassword());
        value.set(passwordAuthentication);
      }
      else {
        setGenericPasswordCanceled(host, port);
      }
    });
    return value.get();
  }

  public PasswordAuthentication getPromptedAuthentication(final String host, final String prompt) {
    if (AUTHENTICATION_CANCELLED) {
      return null;
    }
    final String password = getPlainProxyPassword();
    if (PROXY_AUTHENTICATION) {
      final String login = getSecure("proxy.login");
      if (!StringUtil.isEmptyOrSpaces(login) && !StringUtil.isEmptyOrSpaces(password)) {
        return new PasswordAuthentication(login, password.toCharArray());
      }
    }

    // do not try to show any dialogs if application is exiting
    if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isDisposeInProgress() ||
        ApplicationManager.getApplication().isDisposed()) return null;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myTestGenericAuthRunnable.get();
    }
    final PasswordAuthentication[] value = new PasswordAuthentication[1];
    runAboveAll(() -> {
      if (AUTHENTICATION_CANCELLED) {
        return;
      }

      // password might have changed, and the check below is for that
      final String password1 = getPlainProxyPassword();
      if (PROXY_AUTHENTICATION) {
        final String login = getSecure("proxy.login");
        if (!StringUtil.isEmptyOrSpaces(login) && !StringUtil.isEmptyOrSpaces(password1)) {
          value[0] = new PasswordAuthentication(login, password1.toCharArray());
          return;
        }
      }
      AuthenticationDialog dialog = new AuthenticationDialog(
        PopupUtil.getActiveComponent(),
        "Proxy authentication: " + host,
        "Please enter credentials for: " + prompt,
        getSecure("proxy.login"),
        "",
        KEEP_PROXY_PASSWORD
      );
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        PROXY_AUTHENTICATION = true;
        AuthenticationPanel panel = dialog.getPanel();
        final boolean keepPass = panel.isRememberPassword();
        KEEP_PROXY_PASSWORD = keepPass;
        storeSecure("proxy.login", StringUtil.nullize(panel.getLogin()));
        if (keepPass) {
          setPlainProxyPassword(String.valueOf(panel.getPassword()));
        }
        else {
          removeSecure("proxy.password");
        }
        value[0] = new PasswordAuthentication(panel.getLogin(), panel.getPassword());
      } else {
        AUTHENTICATION_CANCELLED = true;
      }
    });
    return value[0];
  }

  private static void runAboveAll(@NotNull final Runnable runnable) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null && progressIndicator.isModal()) {
      WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(runnable);
    }
    else {
      Application app = ApplicationManager.getApplication();
      app.invokeAndWait(runnable, ModalityState.any());
    }
  }

  /**
   * @deprecated left for compatibility with com.intellij.openapi.project.impl.IdeaServerSettings
   */
  @Deprecated
  public void readExternal(Element element) throws InvalidDataException {
    loadState(XmlSerializer.deserialize(element, HttpConfigurable.class));
  }

  /**
   * @deprecated left for compatibility with com.intellij.openapi.project.impl.IdeaServerSettings
   */
  @Deprecated
  public void writeExternal(Element element) throws WriteExternalException {
    com.intellij.util.xmlb.XmlSerializer.serializeInto(getState(), element);
    if (USE_PROXY_PAC && USE_HTTP_PROXY && !ApplicationManager.getApplication().isDisposed()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
        if (frame != null) {
          USE_PROXY_PAC = false;
          Messages.showMessageDialog(frame.getComponent(), "Proxy: both 'use proxy' and 'autodetect proxy' settings were set." +
                                                           "\nOnly one of these options should be selected.\nPlease re-configure.",
                                     "Proxy Setup", Messages.getWarningIcon());
          editConfigurable(frame.getComponent());
        }
      }, ModalityState.NON_MODAL);
    }
  }

  /**
   * todo [all] It is NOT necessary to call anything if you obey common IDEA proxy settings;
   * todo if you want to define your own behaviour, refer to {@link CommonProxy}
   *
   * also, this method is useful in a way that it test connection to the host [through proxy]
   *
   * @param url URL for HTTP connection
   */
  public void prepareURL(@NotNull String url) throws IOException {
    URLConnection connection = openConnection(url);
    try {
      connection.connect();
      connection.getInputStream();
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable ignored) {
    }
    finally {
      if (connection instanceof HttpURLConnection) {
        ((HttpURLConnection)connection).disconnect();
      }
    }
  }

  @NotNull
  public URLConnection openConnection(@NotNull String location) throws IOException {
    final URL url = new URL(location);
    URLConnection urlConnection = null;
    final List<Proxy> proxies = CommonProxy.getInstance().select(url);
    if (ContainerUtil.isEmpty(proxies)) {
      urlConnection = url.openConnection();
    }
    else {
      IOException exception = null;
      for (Proxy proxy : proxies) {
        try {
          urlConnection = url.openConnection(proxy);
        }
        catch (IOException e) {
          // continue iteration
          exception = e;
        }
      }
      if (urlConnection == null && exception != null) {
        throw exception;
      }
    }

    assert urlConnection != null;
    urlConnection.setReadTimeout(READ_TIMEOUT);
    urlConnection.setConnectTimeout(CONNECTION_TIMEOUT);
    return urlConnection;
  }

  /**
   * Opens HTTP connection to a given location using configured http proxy settings.
   * @param location url to connect to
   * @return instance of {@link HttpURLConnection}
   * @throws IOException in case of any I/O troubles or if created connection isn't instance of HttpURLConnection.
   */
  @NotNull
  public HttpURLConnection openHttpConnection(@NotNull String location) throws IOException {
    URLConnection urlConnection = openConnection(location);
    if (urlConnection instanceof HttpURLConnection) {
      return (HttpURLConnection) urlConnection;
    }
    else {
      throw new IOException("Expected " + HttpURLConnection.class + ", but got " + urlConnection.getClass());
    }
  }

  public boolean isHttpProxyEnabledForUrl(@Nullable String url) {
    if (!USE_HTTP_PROXY) return false;
    URI uri = url != null ? VfsUtil.toUri(url) : null;
    return uri == null || !isProxyException(uri.getHost());
  }

  /** @deprecated use {@link #getJvmProperties(boolean, URI)} (to be removed in IDEA 2018) */
  @Deprecated
  @SuppressWarnings({"unused"})
  public static List<KeyValue<String, String>> getJvmPropertiesList(boolean withAutodetection, @Nullable URI uri) {
    List<Pair<String, String>> properties = getInstance().getJvmProperties(withAutodetection, uri);
    return ContainerUtil.map(properties, p -> KeyValue.create(p.first, p.second));
  }

  @NotNull
  public List<Pair<String, String>> getJvmProperties(boolean withAutodetection, @Nullable URI uri) {
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
      if (proxies != null && !proxies.isEmpty()) {
        for (Proxy proxy : proxies) {
          if (isRealProxy(proxy)) {
            SocketAddress address = proxy.address();
            if (address instanceof InetSocketAddress) {
              InetSocketAddress inetSocketAddress = (InetSocketAddress)address;
              if (Proxy.Type.SOCKS.equals(proxy.type())) {
                result.add(pair(JavaProxyProperty.SOCKS_HOST, inetSocketAddress.getHostName()));
                result.add(pair(JavaProxyProperty.SOCKS_PORT, String.valueOf(inetSocketAddress.getPort())));
              }
              else {
                result.add(pair(JavaProxyProperty.HTTP_HOST, inetSocketAddress.getHostName()));
                result.add(pair(JavaProxyProperty.HTTP_PORT, String.valueOf(inetSocketAddress.getPort())));
                result.add(pair(JavaProxyProperty.HTTPS_HOST, inetSocketAddress.getHostName()));
                result.add(pair(JavaProxyProperty.HTTPS_PORT, String.valueOf(inetSocketAddress.getPort())));
              }
            }
          }
        }
      }
    }
    return result;
  }

  public boolean isProxyException(URI uri) {
    String uriHost = uri.getHost();
    return isProxyException(uriHost);
  }

  @Contract("null -> false")
  public boolean isProxyException(@Nullable String uriHost) {
    if (StringUtil.isEmptyOrSpaces(uriHost) || StringUtil.isEmptyOrSpaces(PROXY_EXCEPTIONS)) {
      return false;
    }

    List<String> hosts = StringUtil.split(PROXY_EXCEPTIONS, ",");
    for (String hostPattern : hosts) {
      String regexpPattern = StringUtil.escapeToRegexp(hostPattern.trim()).replace("\\*", ".*");
      if (Pattern.compile(regexpPattern).matcher(uriHost).matches()) {
        return true;
      }
    }

    return false;
  }

  public static boolean isRealProxy(@NotNull Proxy proxy) {
    return !Proxy.NO_PROXY.equals(proxy) && !Proxy.Type.DIRECT.equals(proxy.type());
  }

  /** @deprecated use {@link com.intellij.execution.configurations.ParametersList#addProperty(String, String)} (to be removed in IDEA 2018) */
  @Deprecated
  @SuppressWarnings({"unused"})
  @NotNull
  public static List<String> convertArguments(@NotNull final List<? extends KeyValue<String, String>> list) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>(list.size());
    for (KeyValue<String, String> value : list) {
      result.add("-D" + value.getKey() + "=" + value.getValue());
    }
    return result;
  }

  public void clearGenericPasswords() {
    synchronized (myLock) {
      myGenericPasswords.clear();
      myGenericCancelled.clear();
    }
  }

  public void removeGeneric(@NotNull CommonProxy.HostInfo info) {
    synchronized (myLock) {
      myGenericPasswords.remove(info);
    }
  }

  public static class ProxyInfo {
    public boolean myStore;
    public String myUsername;
    public String myPasswordCrypt;

    @SuppressWarnings("UnusedDeclaration")
    public ProxyInfo() { }

    public ProxyInfo(boolean store, String username, String passwordCrypt) {
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
      //return PasswordSafe.getInstance().getPassword(null, HttpConfigurable.class, key);
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
      //PasswordSafe.getInstance().storePassword(null, HttpConfigurable.class, key, value);
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
      //PasswordSafe.getInstance().removePassword(null, HttpConfigurable.class, key);
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

}
