// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ui.jcef.JBCefFileSchemeHandler.FILE_SCHEME_NAME;

/**
 * A wrapper over {@link CefApp}.
 * <p>
 * Use {@link #getInstance()} to get the app (triggers CEF startup on first call).
 * Use {@link #createClient()} to create a client.
 *
 * @author tav
 */
@ApiStatus.Experimental
public abstract class JBCefApp {
  private static final Logger LOG = Logger.getInstance(JBCefApp.class);

  @NotNull private final CefApp myCefApp;

  @NotNull private final Disposable myDisposable = new Disposable() {
    @Override
    public void dispose() {
      myCefApp.dispose();
    }
  };

  private static final AtomicBoolean ourInitialized = new AtomicBoolean(false);
  private static final List<JBCefSchemeHandlerFactory> ourSchemeHandlerFactoryList = Collections.synchronizedList(new ArrayList<>());

  private JBCefApp() {
    CefApp.startup();
    //noinspection AbstractMethodCallInConstructor
    CefAppConfig config = getCefAppConfig();
    config.mySettings.windowless_rendering_enabled = false;
    config.mySettings.log_severity = getLogLevel();
    Color bg = JBColor.background();
    config.mySettings.background_color = config.mySettings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());
    if (ApplicationManager.getApplication().isInternal()) {
      config.mySettings.remote_debugging_port = Registry.intValue("ide.browser.jcef.debug.port");
    }
    CefApp.addAppHandler(new MyCefAppHandler(config.myAppArgs));
    myCefApp = CefApp.getInstance(config.mySettings);
    Disposer.register(ApplicationManager.getApplication(), myDisposable);
  }

  private static LogSeverity getLogLevel() {
    String level = System.getProperty("ide.browser.jcef.log.level", "disable").toLowerCase(Locale.ENGLISH);
    switch (level) {
      case "disable":
        return LogSeverity.LOGSEVERITY_DISABLE;
      case "verbose":
        return LogSeverity.LOGSEVERITY_VERBOSE;
      case "info":
        return LogSeverity.LOGSEVERITY_INFO;
      case "warning":
        return LogSeverity.LOGSEVERITY_WARNING;
      case "error":
        return LogSeverity.LOGSEVERITY_ERROR;
      case "fatal":
        return LogSeverity.LOGSEVERITY_FATAL;
      case "default":
      default:
        return LogSeverity.LOGSEVERITY_DEFAULT;
    }
  }

  @NotNull
  Disposable getDisposable() {
    return myDisposable;
  }

  /**
   * Returns {@code JBCefApp} instance. If the app has not yet been initialized
   * then starts up CEF and initializes the app.
   *
   * @throws IllegalStateException when JCEF initialization is not possible in current env
   */
  @NotNull
  public static JBCefApp getInstance() {
    if (!isEnabled()) {
      throw new IllegalStateException("JCEF is disabled");
    }
    if (Holder.instance == null) {
      throw new IllegalStateException("JCEF is not available or failed to initialize");
    }
    return Holder.instance;
  }

  private static class Holder {
    static final JBCefApp instance = initInstance();
  }

  @Nullable
  private static JBCefApp initInstance() {
    ourInitialized.set(true);
    try {
      if (SystemInfo.isMac) {
        return new JBCefAppMac();
      }
      else if (SystemInfo.isLinux) {
        return new JBCefAppLinux();
      }
      else if (SystemInfo.isWindows) {
        return new JBCefAppWindows();
      }
    }
    catch (UnsatisfiedLinkError e) {
      LOG.warn(e.toString());
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
    return null;
  }

  /**
   * Should be used to check if JCEF is enabled in the platform, until it is enabled by default.
   *
   * @deprecated should not be used after JCEF is enabled by default
   */
  @Deprecated
  public static boolean isEnabled() {
    return Registry.is("ide.browser.jcef.enabled");
  }

  public static class CefAppConfig {
    public final @NotNull CefSettings mySettings;
    /**
     * {@link org.cef.handler.CefAppHandler} args.
     */
    public final String @NotNull[] myAppArgs;

    public CefAppConfig(@NotNull CefSettings settings, String @NotNull[] appArgs) {
      this.mySettings = settings;
      this.myAppArgs = appArgs;
    }
  }

  @NotNull
  protected abstract CefAppConfig getCefAppConfig();

  private static class JBCefAppMac extends JBCefApp {
    @NotNull
    @Override
    protected CefAppConfig getCefAppConfig() {
      String ALT_CEF_FRAMEWORK_DIR = System.getenv("ALT_CEF_FRAMEWORK_DIR");
      String ALT_CEF_BROWSER_SUBPROCESS = System.getenv("ALT_CEF_BROWSER_SUBPROCESS");
      if (ALT_CEF_FRAMEWORK_DIR == null || ALT_CEF_BROWSER_SUBPROCESS == null) {
        String CONTENTS_PATH = System.getProperty("java.home") + "/..";
        if (ALT_CEF_FRAMEWORK_DIR == null) {
          ALT_CEF_FRAMEWORK_DIR = CONTENTS_PATH + "/Frameworks/Chromium Embedded Framework.framework";
        }
        if (ALT_CEF_BROWSER_SUBPROCESS == null) {
          ALT_CEF_BROWSER_SUBPROCESS = CONTENTS_PATH + "/Helpers/jcef Helper.app/Contents/MacOS/jcef Helper";
        }
      }
      return new CefAppConfig(new CefSettings(), new String[] {
        "--framework-dir-path=" + normalize(ALT_CEF_FRAMEWORK_DIR),
        "--browser-subprocess-path=" + normalize(ALT_CEF_BROWSER_SUBPROCESS),
        "--disable-in-process-stack-traces",
        "--use-mock-keychain"
      });
    }

    // CEF does not accept ".." in path
    private static String normalize(String path) {
      try {
        return new File(path).getCanonicalPath();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class JBCefAppWindows extends JBCefApp {
    @NotNull
    @Override
    protected CefAppConfig getCefAppConfig() {
      String JCEF_PATH = System.getProperty("java.home") + "/bin";
      CefSettings settings = new CefSettings();
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";
      return new CefAppConfig(settings, ArrayUtil.EMPTY_STRING_ARRAY);
    }
  }

  private static class JBCefAppLinux extends JBCefApp {
    @NotNull
    @Override
    protected CefAppConfig getCefAppConfig() {
      String JCEF_PATH = System.getProperty("java.home") + "/lib";
      CefSettings settings = new CefSettings();
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";
      double scale = JBUIScale.sysScale();
      System.setProperty("jcef.forceDeviceScaleFactor", Double.toString(scale));
      return new CefAppConfig(settings, new String[] {
        "--force-device-scale-factor=" + scale
      });
    }
  }

  @NotNull
  public JBCefClient createClient() {
    return new JBCefClient(myCefApp.createClient());
  }

  /**
   * Adds a scheme handler factory. The method should be called prior to {@code JBCefApp} initialization
   * (performed by {@link #getInstance()}). For instance, via the IDE application service.
   *
   * @throws IllegalStateException if the method is called after {@code JBCefApp} initialization
   */
  public static void addCefSchemeHandlerFactory(@NotNull JBCefSchemeHandlerFactory factory) {
    if (ourInitialized.get()) {
      throw new IllegalStateException("JBCefApp has already been initialized!");
    }
    ourSchemeHandlerFactoryList.add(factory);
  }

  public interface JBCefSchemeHandlerFactory extends CefSchemeHandlerFactory {
    /**
     * A callback to register the scheme handler via calling:
     * {@link CefSchemeRegistrar#addCustomScheme(String, boolean, boolean, boolean, boolean, boolean, boolean, boolean)}.
     */
    void registerCustomScheme(@NotNull CefSchemeRegistrar registrar);

    /**
     * Returns the custom scheme name.
     */
    @NotNull String getSchemeName();

    /**
     * Returns a domain name restricting the scheme.
     * An empty string should be returned when all domains are permitted.
     */
    @NotNull String getDomainName();
  }

  private static class MyCefAppHandler extends CefAppHandlerAdapter {
    MyCefAppHandler(String @Nullable[] args) {
      super(args);
    }

    @Override
    public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
      for (JBCefSchemeHandlerFactory f : ourSchemeHandlerFactoryList) {
        f.registerCustomScheme(registrar);
      }
    }

    @Override
    public void onContextInitialized() {
      for (JBCefSchemeHandlerFactory f : ourSchemeHandlerFactoryList) {
        getInstance().myCefApp.registerSchemeHandlerFactory(f.getSchemeName(), f.getDomainName(), f);
      }
      ourSchemeHandlerFactoryList.clear(); // no longer needed

      getInstance().myCefApp.registerSchemeHandlerFactory(FILE_SCHEME_NAME, "", new CefSchemeHandlerFactory() {
        @Override
        public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
          return FILE_SCHEME_NAME.equals(schemeName) ? new JBCefFileSchemeHandler(browser, frame) : null;
        }
      });
    }
  }
}
