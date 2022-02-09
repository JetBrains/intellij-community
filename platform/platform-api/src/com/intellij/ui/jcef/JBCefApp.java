// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.execution.Platform;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ArrayUtil;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.CefSettings.LogSeverity;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

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

  public static final @NotNull NotNullLazyValue<NotificationGroup> NOTIFICATION_GROUP = NotNullLazyValue.createValue(() -> {
    return NotificationGroup.create("JCEF", NotificationDisplayType.BALLOON, true, null, null, null, null);
  });

  private static final String MISSING_LIBS_SUPPORT_URL = "https://intellij-support.jetbrains.com/hc/en-us/articles/360016421559";

  private static final int MIN_SUPPORTED_CEF_MAJOR_VERSION = 89;
  private static final int MIN_SUPPORTED_JCEF_API_MAJOR_VERSION = 1;
  private static final int MIN_SUPPORTED_JCEF_API_MINOR_VERSION = 5;

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

  private JBCefApp(@NotNull JCefAppConfig config) throws IllegalStateException {
    boolean started = false;
    try {
      started = CefApp.startup(ArrayUtil.EMPTY_STRING_ARRAY);
    } catch (UnsatisfiedLinkError e) {
      LOG.error(e.getMessage());
    }
    if (!started) {
      if (SystemInfoRt.isLinux) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            Process proc = Runtime.getRuntime().exec("ldd " + System.getProperty("java.home") + "/lib/libjcef.so");
            StringBuilder missingLibs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
              String line;
              String delim = " => ";
              String prevLib = null;
              while ((line = reader.readLine()) != null) {
                if (line.contains("not found") && !line.contains("libjvm")) {
                  String[] split = line.split(delim);
                  if (split.length != 2) continue;
                  String lib = split[0];
                  if (lib.equals(prevLib)) continue;
                  if (missingLibs.length() > 0) missingLibs.append(", ");
                  missingLibs.append(lib);
                  prevLib = lib;
                }
              }
            }
            if (proc.waitFor() == 0 && missingLibs.length() > 0) {
              String msg = IdeBundle.message("notification.content.jcef.missingLibs", missingLibs);
              Notification notification = NOTIFICATION_GROUP.getValue().
                createNotification(IdeBundle.message("notification.title.jcef.startFailure"), msg, NotificationType.ERROR);
              //noinspection DialogTitleCapitalization
              notification.addAction(new AnAction(IdeBundle.message("action.jcef.followInstructions")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                  BrowserUtil.open(MISSING_LIBS_SUPPORT_URL);
                }
              });
              notification.notify(null);
            }
          }
          catch (Throwable t) {
            LOG.error("failed to identify JCEF missing libs", t);
          }
        });
      }
      throw new IllegalStateException("CefApp failed to start");
    }
    CefSettings settings = config.getCefSettings();
    settings.windowless_rendering_enabled = isOffScreenRenderingModeEnabled();
    settings.log_severity = getLogLevel();
    settings.log_file = System.getProperty("ide.browser.jcef.log.path",
      System.getProperty("user.home") + Platform.current().fileSeparator + "jcef_" + ProcessHandle.current().pid() + ".log");
    //todo[tav] IDEA-260446 & IDEA-260344 However, without proper background the CEF component flashes white in dark themes
    //settings.background_color = settings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());
    int port = Registry.intValue("ide.browser.jcef.debug.port");
    if (ApplicationManager.getApplication().isInternal() && port > 0) {
      settings.remote_debugging_port = port;
    }

    String[] argsFromProviders = JBCefAppRequiredArgumentsProvider
      .getProviders()
      .stream()
      .flatMap(p -> {
        LOG.debug("got options: [" + p.getOptions() + "] from:" + p.getClass().getName());
        return p.getOptions().stream();
      })
      .distinct()
      .toArray(String[]::new);

    String[] args = ArrayUtil.mergeArrays(config.getAppArgs(), argsFromProviders);

    JBCefProxySettings proxySettings = JBCefProxySettings.getInstance();
    String[] proxyArgs = null;
    if (proxySettings.USE_PROXY_PAC) {
      if (proxySettings.USE_PAC_URL) {
        proxyArgs = new String[] {"--proxy-pac-url=" + proxySettings.PAC_URL + ":" + proxySettings.PROXY_PORT};
      }
      else {
        proxyArgs = new String[] {"--proxy-auto-detect"};
      }
    }
    else if (proxySettings.USE_HTTP_PROXY) {
      proxyArgs = new String[] {"--proxy-server=" + proxySettings.PROXY_HOST + ":" + proxySettings.PROXY_PORT};
    }
    if (proxyArgs != null) args = ArrayUtil.mergeArrays(args, proxyArgs);

    if (Registry.is("ide.browser.jcef.gpu.disable")) {
      // Add possibility to disable GPU (see IDEA-248140)
      args = ArrayUtil.mergeArrays(args, "--disable-gpu", "--disable-gpu-compositing");
    }

    final boolean trackGPUCrashes = Registry.is("ide.browser.jcef.gpu.infinitecrash");
    if (trackGPUCrashes) args = ArrayUtil.mergeArrays(args, "--disable-gpu-process-crash-limit");

    // Sometimes it's useful to be able to pass any additional keys (see IDEA-248140)
    // NOTE: List of keys: https://peter.sh/experiments/chromium-command-line-switches/
    String extraArgsProp = System.getProperty("ide.browser.jcef.extra.args", "");
    if (!extraArgsProp.isEmpty()) {
      String[] extraArgs = extraArgsProp.split(" ");
      if (extraArgs.length > 0) {
        LOG.debug("add extra CEF args: [" + Arrays.toString(extraArgs) + "]");
        args = ArrayUtil.mergeArrays(args, extraArgs);
      }
    }

    CefApp.addAppHandler(new MyCefAppHandler(args, trackGPUCrashes));
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
      synchronized (Holder.class) {
        if (Holder.INSTANCE == null) {
          if (RegistryManager.getInstance().is("ide.browser.jcef.testMode.enabled")) {
            // Try again to initialize with probably different registry keys
            Holder.INSTANCE = Holder.init();
            if (Holder.INSTANCE != null) {
              return Objects.requireNonNull(Holder.INSTANCE);
            }
          }
          throw new IllegalStateException("JCEF is not supported in this env or failed to initialize");
        }
      }
    }
    return Objects.requireNonNull(Holder.INSTANCE);
  }

  private static final class Holder {
    @Nullable static volatile JBCefApp INSTANCE = init();

    @Nullable
    static JBCefApp init() {
      ourInitialized.set(true);
      JCefAppConfig config = null;
      if (isSupported()) {
        try {
          if (!JreHiDpiUtil.isJreHiDPIEnabled()) {
            System.setProperty("jcef.forceDeviceScaleFactor", String.valueOf(getForceDeviceScaleFactor()));
          }
          config = JCefAppConfig.getInstance();
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      JBCefApp app = null;
      if (config != null) {
        try {
          app = new JBCefApp(config);
        } catch (IllegalStateException ignore) {
        }
      }
      return app;
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
    boolean testModeEnabled = RegistryManager.getInstance().is("ide.browser.jcef.testMode.enabled");
    if (ourSupported != null && !testModeEnabled) {
      return ourSupported.get();
    }
    synchronized (ourSupportedLock) {
      if (testModeEnabled) {
        ourSupported = null;
      }
      else if (ourSupported != null) {
        return ourSupported.get();
      }
      Function<String, Boolean> unsupported = (msg) -> {
        ourSupported = new AtomicBoolean(false);
        LOG.warn(msg + (!msg.contains("disabled") ? " (Use JBR bundled with the IDE)" : ""));
        return false;
      };
      // warn: do not change to Registry.is(), the method used at startup
      if (!RegistryManager.getInstance().is("ide.browser.jcef.enabled")) {
        return unsupported.apply("JCEF is manually disabled via 'ide.browser.jcef.enabled=false'");
      }
      if (GraphicsEnvironment.isHeadless() &&
          !RegistryManager.getInstance().is("ide.browser.jcef.headless.enabled"))
      {
        return unsupported.apply("JCEF is manually disabled in headless env via 'ide.browser.jcef.headless.enabled=false'");
      }
      JCefVersionDetails version;
      try {
        version = JCefAppConfig.getVersionDetails();
      }
      catch (Throwable e) {
        return unsupported.apply("JCEF runtime version is not supported");
      }
      if (MIN_SUPPORTED_CEF_MAJOR_VERSION > version.cefVersion.major) {
        return unsupported.apply("JCEF: minimum supported CEF major version is " + MIN_SUPPORTED_CEF_MAJOR_VERSION +
                                 ", current is " + version.cefVersion.major);
      }
      if (MIN_SUPPORTED_JCEF_API_MAJOR_VERSION > version.apiVersion.major ||
          (MIN_SUPPORTED_JCEF_API_MAJOR_VERSION == version.apiVersion.major &&
           MIN_SUPPORTED_JCEF_API_MINOR_VERSION > version.apiVersion.minor))
      {
        return unsupported.apply("JCEF: minimum supported API version is " +
                                 MIN_SUPPORTED_JCEF_API_MAJOR_VERSION + "." + MIN_SUPPORTED_JCEF_API_MINOR_VERSION +
                                 ", current is " + version.apiVersion.major + "." + version.apiVersion.minor);
      }
      URL url = JCefAppConfig.class.getResource("JCefAppConfig.class");
      if (url == null) {
        return unsupported.apply("JCefAppConfig.class not found");
      }
      String path = url.toString();
      String name = JCefAppConfig.class.getName().replace('.', '/');
      boolean isJbrModule = path != null && path.contains("/jcef/" + name);
      if (!isJbrModule) {
        return unsupported.apply("JCEF runtime library is not a JBR module");
      }
      ourSupported = new AtomicBoolean(true);
      return true;
    }
  }

  /**
   * Returns {@code true} if JCEF has successfully started.
   */
  public static boolean isStarted() {
    boolean initialised = ourInitialized.get();
    if (!initialised) return false;
    //noinspection ConstantConditions
    return getInstance() != null;
  }

  @NotNull
  public JBCefClient createClient() {
    return createClient(false);
  }

  @NotNull
  JBCefClient createClient(boolean isDefault) {
    return new JBCefClient(myCefApp.createClient(), isDefault);
  }

  /**
   * Returns true if the off-screen rendering mode is enabled.
   * <p></p>
   * This mode allows for browser creation in either windowed or off-screen rendering mode.
   *
   * @see JBCefOsrHandlerBrowser
   * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
   */
  public static boolean isOffScreenRenderingModeEnabled() {
    return RegistryManager.getInstance().is("ide.browser.jcef.osr.enabled");
  }

  static void checkOffScreenRenderingModeEnabled() {
    if (!isOffScreenRenderingModeEnabled()) {
      throw new IllegalStateException("off-screen rendering mode is disabled: 'ide.browser.jcef.osr.enabled=false'");
    }
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
    private final int myGPUCrashLimit;
    private int myGPUCrashCounter = 0;
    private boolean myNotificationShown = false;

    MyCefAppHandler(String @Nullable[] args, boolean trackGPUCrashes) {
      super(args);
      myGPUCrashLimit = trackGPUCrashes ? Integer.getInteger("ide.browser.jcef.gpu.infinitecrash.internallimit", 10) : -1;
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

    //@Override
    public void onBeforeChildProcessLaunch(String command_line) {
      if (myGPUCrashLimit >= 0 && command_line != null && command_line.contains("--type=gpu-process")) {
        if (++myGPUCrashCounter > myGPUCrashLimit && !myNotificationShown) {
          Notification notification = NOTIFICATION_GROUP.getValue().createNotification(
            IdeBundle.message("notification.content.jcef.gpucrash.title"),
            IdeBundle.message("notification.content.jcef.gpucrash.message"),
            NotificationType.ERROR);
          //noinspection DialogTitleCapitalization
          notification.addAction(new AnAction(IdeBundle.message("notification.content.jcef.gpucrash.action.restart")) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
              ApplicationManager.getApplication().restart();
            }
          });
          //noinspection DialogTitleCapitalization
          if (!Registry.is("ide.browser.jcef.gpu.disable")) {
            notification.addAction(new AnAction(IdeBundle.message("notification.content.jcef.gpucrash.action.disable")) {
              @Override
              public void actionPerformed(@NotNull AnActionEvent e) {
                Registry.get("ide.browser.jcef.gpu.disable").setValue(true);
                ApplicationManager.getApplication().restart();
              }
            });
          }
          notification.notify(null);
          myNotificationShown = true;
        }
      }
    }
  }

  /**
   * Used to force JCEF scale in IDE-managed HiDPI mode.
   */
  public static double getForceDeviceScaleFactor() {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? -1 : ScaleContext.create().getScale(DerivedScaleType.PIX_SCALE);
  }

  /**
   * Returns normal (unscaled) size of the provided scaled size if IDE-managed HiDPI mode is enabled.
   * In JRE-managed HiDPI mode the method has no effect.
   * <p></p>
   * This method should be applied to size values (for instance, font size) previously scaled (explicitly or implicitly)
   * via {@link com.intellij.ui.scale.JBUIScale#scale(int)}, before the values are used in html (in CSS, for instance).
   *
   * @see com.intellij.ui.scale.ScaleType
   */
  public static int normalizeScaledSize(int scaledSize) {
    return JreHiDpiUtil.isJreHiDPIEnabled() ? scaledSize : ROUND.round(scaledSize / getForceDeviceScaleFactor());
  }
}
