// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JBColor;
import com.intellij.util.ArrayUtil;
import com.jetbrains.cef.JCefAppConfig;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.CefSettings.LogSeverity;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A wrapper over {@link CefApp}.
 * <p>
 * Use {@link #getInstance()} to get the app (triggers CEF startup on first call).
 * Use {@link #createClient()} to create a client.
 *
 * @author tav
 */
public final class JBCefApp {
  private static final Logger LOG = Logger.getInstance(JBCefApp.class);

  // [tav] todo: retrieve the version at compile time from the "jcef" maven lib
  private static final int MIN_SUPPORTED_CEF_MAJOR_VERSION = 77;

  @NotNull private final CefApp myCefApp;

  @NotNull private final Disposable myDisposable = new Disposable() {
    @Override
    public void dispose() {
      myCefApp.dispose();
    }
  };

  private static volatile AtomicBoolean ourSupported;
  private static final Object ourSupportedLock = new Object();

  private static final AtomicBoolean ourInitialized = new AtomicBoolean(false);
  private static final List<JBCefCustomSchemeHandlerFactory> ourCustomSchemeHandlerFactoryList =
    Collections.synchronizedList(new ArrayList<>());

  //fixme use addCefCustomSchemeHandlerFactory method if possible
  private static final JBCefSourceSchemeHandlerFactory ourSourceSchemeHandlerFactory = new JBCefSourceSchemeHandlerFactory();

  private JBCefApp(@NotNull JCefAppConfig config) {
    CefApp.startup(ArrayUtil.EMPTY_STRING_ARRAY);
    CefSettings settings = config.getCefSettings();
    settings.windowless_rendering_enabled = false;
    settings.log_severity = getLogLevel();
    Color bg = JBColor.background();
    settings.background_color = settings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());
    int port = Registry.intValue("ide.browser.jcef.debug.port");
    if (ApplicationManager.getApplication().isInternal() && port > 0) {
      settings.remote_debugging_port = port;
    }
    CefApp.addAppHandler(new MyCefAppHandler(config.getAppArgs()));
    myCefApp = CefApp.getInstance(settings);
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
    if (Holder.INSTANCE == null) {
      throw new IllegalStateException("JCEF is not supported in this env or failed to initialize");
    }
    return Holder.INSTANCE;
  }

  private static final class Holder {
    @Nullable static final JBCefApp INSTANCE;

    static {
      ourInitialized.set(true);
      JCefAppConfig config = null;
      if (isSupported(true)) {
        try {
          config = JCefAppConfig.getInstance();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      INSTANCE = config != null ? new JBCefApp(config) : null;
    }
  }

  /**
   * Returns whether JCEF is supported. For that:
   * <ul>
   * <li> It should be available in the running JBR.
   * <li> It should have a compatible version.
   * </ul>
   * In order to assuredly meet the above requirements the IDE should run with a bundled JBR.
   */
  public static boolean isSupported() {
    return isSupported(false);
  }

  private static boolean isSupported(boolean logging) {
    if (ourSupported != null) {
      return ourSupported.get();
    }
    synchronized (ourSupportedLock) {
      if (ourSupported != null) {
        return ourSupported.get();
      }
      Function<String, Boolean> unsupported = (msg) -> {
        ourSupported = new AtomicBoolean(false);
        if (logging) {
          LOG.warn(msg + (!msg.contains("disabled") ? " (Use JBR bundled with the IDE)" : ""));
        }
        return false;
      };
      // warn: do not change to Registry.is(), the method used at startup
      if (!RegistryManager.getInstance().is("ide.browser.jcef.enabled")) {
        return unsupported.apply("JCEF is manually disabled via 'ide.browser.jcef.enabled=false'");
      }
      if (ApplicationManager.getApplication().isHeadlessEnvironment() &&
          !RegistryManager.getInstance().is("ide.browser.jcef.headless.enabled"))
      {
        return unsupported.apply("JCEF is manually disabled in headless env via 'ide.browser.jcef.headless.enabled=false'");
      }
      String version;
      try {
        version = JCefAppConfig.getVersion();
      }
      catch (NoSuchMethodError e) {
        return unsupported.apply("JCEF runtime version is not supported");
      }
      if (version == null) {
        return unsupported.apply("JCEF runtime version is not available");
      }
      String[] split = version.split("\\.");
      if (split.length == 0) {
        return unsupported.apply("JCEF runtime version has wrong format: " + version);
      }
      try {
        int majorVersion = Integer.parseInt(split[0]);
        if (MIN_SUPPORTED_CEF_MAJOR_VERSION > majorVersion) {
          return unsupported.apply("JCEF minimum supported major version is " + MIN_SUPPORTED_CEF_MAJOR_VERSION +
                                   ", current is " + majorVersion);
        }
      }
      catch (NumberFormatException e) {
        return unsupported.apply("JCEF runtime version has wrong format: " + version);
      }

      String path = JCefAppConfig.class.getResource("JCefAppConfig.class").toString();
      String name = JCefAppConfig.class.getName().replace('.', '/');
      boolean isJbrModule = path != null && path.contains("/jcef/" + name);
      if (!isJbrModule) {
        return unsupported.apply("JCEF runtime library is not a JBR module");
      }
      ourSupported = new AtomicBoolean(true);
      return true;
    }
  }

  @NotNull
  public JBCefClient createClient() {
    return new JBCefClient(myCefApp.createClient());
  }

  /**
   * Adds a custom scheme handler factory.
   * <p>
   * The method must be called prior to {@code JBCefApp} initialization
   * (performed by {@link #getInstance()}). For instance, via the IDE application service.
   * <p>
   * The method should not be called for built-in schemes ("html", "file", etc).
   *
   * @throws IllegalStateException if the method is called after {@code JBCefApp} initialization
   */
  @SuppressWarnings("unused")
  /*public*/ static void addCefCustomSchemeHandlerFactory(@NotNull JBCefApp.JBCefCustomSchemeHandlerFactory factory) {
    if (ourInitialized.get()) {
      throw new IllegalStateException("JBCefApp has already been initialized!");
    }
    ourCustomSchemeHandlerFactoryList.add(factory);
  }

  public interface JBCefCustomSchemeHandlerFactory extends CefSchemeHandlerFactory {
    /**
     * A callback to register the custom scheme handler via calling:
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
    public boolean onBeforeTerminate() {
      // Do not let JCEF auto-terminate by Cmd+Q (or an alternative),
      // so that IDE (user) has an option to decide
      return true;
    }

    @Override
    public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
      for (JBCefCustomSchemeHandlerFactory f : ourCustomSchemeHandlerFactoryList) {
        f.registerCustomScheme(registrar);
      }
      ourSourceSchemeHandlerFactory.registerCustomScheme(registrar);
    }

    @Override
    public void onContextInitialized() {
      for (JBCefCustomSchemeHandlerFactory f : ourCustomSchemeHandlerFactoryList) {
        getInstance().myCefApp.registerSchemeHandlerFactory(f.getSchemeName(), f.getDomainName(), f);
      }
      ourCustomSchemeHandlerFactoryList.clear(); // no longer needed

      getInstance().myCefApp.registerSchemeHandlerFactory(
        ourSourceSchemeHandlerFactory.getSchemeName(), ourSourceSchemeHandlerFactory.getDomainName(), ourSourceSchemeHandlerFactory);

      getInstance().myCefApp.registerSchemeHandlerFactory(
        JBCefFileSchemeHandlerFactory.FILE_SCHEME_NAME, "", new JBCefFileSchemeHandlerFactory());
    }
  }
}
