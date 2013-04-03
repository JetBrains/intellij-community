/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.net;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.JavaProxyProperty;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 7, 2003
 * Time: 3:58:23 PM
 * To change this template use Options | File Templates.
 */
@State(
  name = "HttpConfigurable",
  storages = {
    @Storage( file = StoragePathMacros.APP_CONFIG + "/other.xml")
  }
)
public class HttpConfigurable implements PersistentStateComponent<HttpConfigurable>, ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.net.HttpConfigurable");
  public boolean PROXY_TYPE_IS_SOCKS = false;
  public boolean USE_HTTP_PROXY = false;
  public boolean USE_PROXY_PAC = false;
  public volatile transient boolean AUTHENTICATION_CANCELLED = false;
  public String PROXY_HOST = "";
  public int PROXY_PORT = 80;

  public volatile boolean PROXY_AUTHENTICATION = false;
  public volatile String PROXY_LOGIN = "";
  public volatile String PROXY_PASSWORD_CRYPT = "";
  public boolean KEEP_PROXY_PASSWORD = false;
  public transient String LAST_ERROR;
  public Map<CommonProxy.HostInfo, ProxyInfo> myGenericPasswords = new HashMap<CommonProxy.HostInfo, ProxyInfo>();
  public Set<CommonProxy.HostInfo> myGenericCancelled = new HashSet<CommonProxy.HostInfo>();
  private transient final Object myLock = new Object();
  private IdeaWideProxySelector mySelector;
  private IdeaWideAuthenticator myAuthenticator;
  public transient Getter<PasswordAuthentication> myTestAuthRunnable = new StaticGetter<PasswordAuthentication>(null);
  public transient Getter<PasswordAuthentication> myTestGenericAuthRunnable = new StaticGetter<PasswordAuthentication>(null);
  public String PROXY_EXCEPTIONS = "";

  public static HttpConfigurable getInstance() {
    return ServiceManager.getService(HttpConfigurable.class);
  }

  public static boolean editConfigurable(final JComponent parent) {
    return ShowSettingsUtil.getInstance().editConfigurable(parent, new HTTPProxySettingsPanel(getInstance()));
  }

  @Override
  public HttpConfigurable getState() {
    final HttpConfigurable state = new HttpConfigurable();
    XmlSerializerUtil.copyBean(this, state);
    if (!KEEP_PROXY_PASSWORD) {
      state.PROXY_PASSWORD_CRYPT = "";
    }
    correctPasswords(this, state);
    return state;
  }

  @Override
  public void initComponent() {
    mySelector = new IdeaWideProxySelector(this);
    myAuthenticator = new IdeaWideAuthenticator(this);
    final String name = getClass().getName();
    CommonProxy.getInstance().setCustom(name, mySelector);
    CommonProxy.getInstance().setCustomAuth(name, myAuthenticator);
  }

  @NotNull
  public ProxySelector getOnlyBySettingsSelector() {
    return mySelector;
  }

  @Override
  public void disposeComponent() {
    final String name = getClass().getName();
    CommonProxy.getInstance().removeCustom(name);
    CommonProxy.getInstance().removeCustomAuth(name);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return getClass().getName();
  }

  private void correctPasswords(HttpConfigurable from, HttpConfigurable to) {
    synchronized (myLock) {
      to.myGenericPasswords = new HashMap<CommonProxy.HostInfo, ProxyInfo>();
      for (Map.Entry<CommonProxy.HostInfo, ProxyInfo> entry : from.myGenericPasswords.entrySet()) {
        if (Boolean.TRUE.equals(entry.getValue().isStore())) {
          to.myGenericPasswords.put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  @Override
  public void loadState(HttpConfigurable state) {
    XmlSerializerUtil.copyBean(state, this);
    if (!KEEP_PROXY_PASSWORD) {
      PROXY_PASSWORD_CRYPT = "";
    }
    correctPasswords(state, this);
  }

  public boolean isGenericPasswordCanceled(final String host, final int port) {
    synchronized (myLock) {
      return myGenericCancelled.contains(Pair.create(host, port));
    }
  }

  public void setGenericPasswordCanceled(final String host, final int port) {
    synchronized (myLock) {
      myGenericCancelled.add(new CommonProxy.HostInfo("", host, port));
    }
  }

  public PasswordAuthentication getGenericPassword(final String host, final int port) {
    final ProxyInfo proxyInfo;
    synchronized (myLock) {
      proxyInfo = myGenericPasswords.get(new CommonProxy.HostInfo("", host, port));
    }
    if (proxyInfo == null) return null;
    return new PasswordAuthentication(proxyInfo.getUsername(), decode(String.valueOf(proxyInfo.getPasswordCrypt())).toCharArray());
  }

  public void putGenericPassword(final String host, final int port, final PasswordAuthentication authentication, final boolean remember) {
    final PasswordAuthentication coded = new PasswordAuthentication(authentication.getUserName(), encode(String.valueOf(authentication.getPassword())).toCharArray());
    synchronized (myLock) {
      myGenericPasswords.put(new CommonProxy.HostInfo("", host, port), new ProxyInfo(remember, coded.getUserName(), String.valueOf(
        coded.getPassword())));
    }
  }

  @Transient
  public String getPlainProxyPassword() {
    return decode(PROXY_PASSWORD_CRYPT);
  }

  private String decode(String value) {
    return new String(new Base64().decode(value.getBytes()));
  }

  @Transient
  public void setPlainProxyPassword (String password) {
    PROXY_PASSWORD_CRYPT = encode(password);
  }

  private String encode(String password) {
    return new String(new Base64().encode(password.getBytes()));
  }

  public PasswordAuthentication getGenericPromptedAuthentication(final String prefix, final String host, final String prompt, final int port, final boolean remember) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myTestGenericAuthRunnable.get();
    }
    final PasswordAuthentication[] value = new PasswordAuthentication[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        if (isGenericPasswordCanceled(host, port)) return;
        final PasswordAuthentication password = getGenericPassword(host, port);
        if (password != null) {
          value[0] = password;
          return;
        }
        final AuthenticationDialog dlg = new AuthenticationDialog(PopupUtil.getActiveComponent(), prefix + host,
                                                                  "Please enter credentials for: " + prompt, "", "", remember);
        dlg.show();
        if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          final AuthenticationPanel panel = dlg.getPanel();
          final boolean remember1 = remember && panel.isRememberPassword();
          value[0] = new PasswordAuthentication(panel.getLogin(), panel.getPassword());
          putGenericPassword(host, port, value[0], remember1);
        } else {
          setGenericPasswordCanceled(host, port);
        }
      }
    };
    runAboveAll(runnable);
    return value[0];
  }

  public PasswordAuthentication getPromptedAuthentication(final String host, final String prompt) {
    if (AUTHENTICATION_CANCELLED) return null;
    final String password = getPlainProxyPassword();
    if (PROXY_AUTHENTICATION && ! StringUtil.isEmptyOrSpaces(PROXY_LOGIN) && ! StringUtil.isEmptyOrSpaces(password)) {
      return new PasswordAuthentication(PROXY_LOGIN, password.toCharArray());
    }

    // do not try to show any dialogs if application is exiting
    if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isDisposeInProgress() ||
        ApplicationManager.getApplication().isDisposed()) return null;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myTestGenericAuthRunnable.get();
    }
    final String login = PROXY_LOGIN == null ? "" : PROXY_LOGIN;
    final PasswordAuthentication[] value = new PasswordAuthentication[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        if (AUTHENTICATION_CANCELLED) return;
        // password might have changed, and the check below is for that
        final String password = getPlainProxyPassword();
        if (PROXY_AUTHENTICATION && ! StringUtil.isEmptyOrSpaces(PROXY_LOGIN) && ! StringUtil.isEmptyOrSpaces(password)) {
          value[0] = new PasswordAuthentication(PROXY_LOGIN, password.toCharArray());
          return;
        }
        final AuthenticationDialog dlg = new AuthenticationDialog(PopupUtil.getActiveComponent(), "Proxy authentication: " + host,
                                                                  "Please enter credentials for: " + prompt, login, "", KEEP_PROXY_PASSWORD);
        dlg.show();
        if (dlg.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          PROXY_AUTHENTICATION = true;
          final AuthenticationPanel panel = dlg.getPanel();
          KEEP_PROXY_PASSWORD = panel.isRememberPassword();
          PROXY_LOGIN = panel.getLogin();
          setPlainProxyPassword(String.valueOf(panel.getPassword()));
          value[0] = new PasswordAuthentication(panel.getLogin(), panel.getPassword());
        } else {
          AUTHENTICATION_CANCELLED = true;
        }
      }
    };
    runAboveAll(runnable);
    return value[0];
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void runAboveAll(final Runnable runnable) {
    final Runnable throughSwing = new Runnable() {
      @Override
      public void run() {
        if (SwingUtilities.isEventDispatchThread()) {
          runnable.run();
          return;
        }
        try {
          SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException e) {
          LOG.info(e);
        }
        catch (InvocationTargetException e) {
          LOG.info(e);
        }
      }
    };
    if (ProgressManager.getInstance().getProgressIndicator() != null) {
      if (ProgressManager.getInstance().getProgressIndicator().isModal()) {
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(runnable);
      } else {
        throughSwing.run();
      }
    } else {
      throughSwing.run();
    }
  }

  //these methods are preserved for compatibility
  @Override
  public void readExternal(Element element) throws InvalidDataException {
    loadState(XmlSerializer.deserialize(element, HttpConfigurable.class));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    CommonProxy.isInstalledAssertion();
    XmlSerializer.serializeInto(getState(), element);
    if (USE_PROXY_PAC && USE_HTTP_PROXY && ! ApplicationManager.getApplication().isDisposed()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
          if (frame != null) {
            USE_PROXY_PAC = false;
            Messages.showMessageDialog(frame.getComponent(), "Proxy: both 'use proxy' and 'autodetect proxy' settings were set." +
                                                             "\nOnly one of these options should be selected.\nPlease re-configure.",
                                       "Proxy setup", Messages.getWarningIcon());
            editConfigurable(frame.getComponent());
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  /**
   * todo [all] It is NOT nessesary to call anything if you obey common IDEA proxy settings;
   * todo if you want to define your own behaviour, refer to {@link com.intellij.util.proxy.CommonProxy}
   *
   * also, this method is useful in a way that it test connection to the host [through proxy]
   *
   * @param url URL for HTTP connection
   * @throws IOException
   */
  public void prepareURL (String url) throws IOException {
    //setAuthenticator();
    CommonProxy.isInstalledAssertion();

    final URLConnection connection = openConnection(url);
    try {
      connection.setConnectTimeout(3 * 1000);
      connection.setReadTimeout(3 * 1000);
      connection.connect();
      connection.getInputStream();
    }
    catch (Throwable e) {
      if (e instanceof IOException) {
        throw (IOException)e;
      }
    } finally {
      if (connection instanceof HttpURLConnection) {
        ((HttpURLConnection)connection).disconnect();
      }
    }
  }

  public URLConnection openConnection(@NotNull String location) throws IOException {
    CommonProxy.isInstalledAssertion();
    final URL url = new URL(location);
    URLConnection urlConnection = null;
    final List<Proxy> proxies = CommonProxy.getInstance().select(url);
    if (proxies == null || proxies.isEmpty()) {
      urlConnection = url.openConnection();
    } else {
      IOException ioe = null;
      for (Proxy proxy : proxies) {
        try {
          urlConnection = url.openConnection(proxy);
        } catch (IOException e) {
          // continue iteration
          ioe = e;
        }
      }
      if (urlConnection == null && ioe != null) {
        throw ioe;
      }
    }
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

  public static List<KeyValue<String, String>> getJvmPropertiesList(final boolean withAutodetection, @Nullable final URI uri) {
    final HttpConfigurable me = getInstance();
    if (! me.USE_HTTP_PROXY && ! me.USE_PROXY_PAC) {
      return Collections.emptyList();
    }
    final List<KeyValue<String, String>> result = new ArrayList<KeyValue<String, String>>();
    if (me.USE_HTTP_PROXY) {
      final boolean putCredentials = me.KEEP_PROXY_PASSWORD && StringUtil.isNotEmpty(me.PROXY_LOGIN);
      if (me.PROXY_TYPE_IS_SOCKS) {
        result.add(KeyValue.create(JavaProxyProperty.SOCKS_HOST, me.PROXY_HOST));
        result.add(KeyValue.create(JavaProxyProperty.SOCKS_PORT, String.valueOf(me.PROXY_PORT)));
        if (putCredentials) {
          result.add(KeyValue.create(JavaProxyProperty.SOCKS_USERNAME, me.PROXY_LOGIN));
          result.add(KeyValue.create(JavaProxyProperty.SOCKS_PASSWORD, me.getPlainProxyPassword()));
        }
      } else {
        result.add(KeyValue.create(JavaProxyProperty.HTTP_HOST, me.PROXY_HOST));
        result.add(KeyValue.create(JavaProxyProperty.HTTP_PORT, String.valueOf(me.PROXY_PORT)));
        result.add(KeyValue.create(JavaProxyProperty.HTTPS_HOST, me.PROXY_HOST));
        result.add(KeyValue.create(JavaProxyProperty.HTTPS_PORT, String.valueOf(me.PROXY_PORT)));
        if (putCredentials) {
          result.add(KeyValue.create(JavaProxyProperty.HTTP_USERNAME, me.PROXY_LOGIN));
          result.add(KeyValue.create(JavaProxyProperty.HTTP_PASSWORD, me.getPlainProxyPassword()));
        }
      }
    } else if (me.USE_PROXY_PAC && withAutodetection && uri != null) {
      final List<Proxy> proxies = CommonProxy.getInstance().select(uri);
      // we will just take the first returned proxy, but we have an option to test connection through each of them,
      // for instance, by calling prepareUrl()
      if (proxies != null && ! proxies.isEmpty()) {
        for (Proxy proxy : proxies) {
          if (isRealProxy(proxy)) {
            final SocketAddress address = proxy.address();
            if (address instanceof InetSocketAddress) {
              final InetSocketAddress inetSocketAddress = (InetSocketAddress)address;
              if (Proxy.Type.SOCKS.equals(proxy.type())) {
                result.add(KeyValue.create(JavaProxyProperty.SOCKS_HOST, inetSocketAddress.getHostName()));
                result.add(KeyValue.create(JavaProxyProperty.SOCKS_PORT, String.valueOf(inetSocketAddress.getPort())));
              } else {
                result.add(KeyValue.create(JavaProxyProperty.HTTP_HOST, inetSocketAddress.getHostName()));
                result.add(KeyValue.create(JavaProxyProperty.HTTP_PORT, String.valueOf(inetSocketAddress.getPort())));
                result.add(KeyValue.create(JavaProxyProperty.HTTPS_HOST, inetSocketAddress.getHostName()));
                result.add(KeyValue.create(JavaProxyProperty.HTTPS_PORT, String.valueOf(inetSocketAddress.getPort())));
              }
            }
          }
        }
      }
    }
    return result;
  }

  public static boolean isRealProxy(Proxy proxy) {
    return ! Proxy.NO_PROXY.equals(proxy) && ! Proxy.Type.DIRECT.equals(proxy.type());
  }

  public static List<String> convertArguments(@NotNull final List<KeyValue<String, String>> list) {
    if (list.isEmpty()) return Collections.emptyList();
    final List<String> result = new ArrayList<String>(list.size());
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

  public void removeGeneric(CommonProxy.HostInfo info) {
    synchronized (myLock) {
      myGenericPasswords.remove(info);
    }
  }

  public static class ProxyInfo {
    public boolean myStore;
    public String myUsername;
    public String myPasswordCrypt;

    public ProxyInfo() {
    }

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

    public void setPasswordCrypt(String passwordCrypt) {
      myPasswordCrypt = passwordCrypt;
    }
  }
}
