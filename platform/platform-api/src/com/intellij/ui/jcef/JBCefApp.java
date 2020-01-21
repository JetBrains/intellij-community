// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ArrayUtil;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tav
 */
@ApiStatus.Experimental
public abstract class JBCefApp {
  private static JBCefApp INSTANCE;
  @NotNull private final CefApp myCefApp;

  private static final AtomicBoolean ourInitialized = new AtomicBoolean(false);
  private static final List<JBCefSchemeHandlerFactory> ourSchemeHandlerFactoryList = Collections.synchronizedList(new ArrayList<>());

  private JBCefApp() {
    CefApp.startup();
    //noinspection AbstractMethodCallInConstructor
    CefAppConfig config = getCefAppConfig();
    config.mySettings.windowless_rendering_enabled = false;
    config.mySettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_ERROR;
    Color bg = JBColor.background();
    config.mySettings.background_color = config.mySettings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());
    CefApp.addAppHandler(new MyCefAppHandler(config.myAppArgs));
    myCefApp = CefApp.getInstance(config.mySettings);
    Disposer.register(ApplicationManager.getApplication(), () -> {
      myCefApp.dispose();
    });
  }

  /**
   * Returns {@code JBCefApp} instance. If the app has not yet been initialized
   * then starts up CEF and initializes the app.
   *
   * @throws IllegalStateException when JCEF initialization is not possible in current env
   */
  @NotNull
  public static JBCefApp getInstance() {
    if (!Registry.is("ide.browser.jcef.enabled")) {
      throw new IllegalStateException("JCEF is disabled");
    }
    if (!ourInitialized.getAndSet(true)) {
      if (SystemInfo.isMac) {
        INSTANCE = new JBCefAppMac();
      }
      else if (SystemInfo.isLinux) {
        INSTANCE = new JBCefAppLinux();
      }
      else if (SystemInfo.isWindows) {
        INSTANCE = new JBCefAppWindows();
      }
      else {
        throw new IllegalStateException("JCEF is initialized on unsupported platform");
      }
    }
    return INSTANCE;
  }

  public static class CefAppConfig {
    public final @NotNull CefSettings mySettings;
    /**
     * {@link org.cef.handler.CefAppHandler} args.
     */
    public final @NotNull String[] myAppArgs;

    public CefAppConfig(@NotNull CefSettings settings, @NotNull String[] appArgs) {
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
      String JCEF_FRAMEWORKS_PATH = System.getProperty("java.home") + "/Frameworks";
      return new CefAppConfig(new CefSettings(), new String[] {
        "--framework-dir-path=" + JCEF_FRAMEWORKS_PATH + "/Chromium Embedded Framework.framework",
        "--browser-subprocess-path=" + JCEF_FRAMEWORKS_PATH + "/jcef Helper.app/Contents/MacOS/jcef Helper",
        "--disable-in-process-stack-traces"
      });
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
      return new CefAppConfig(settings, new String[] {
        "--force-device-scale-factor=" + JBUIScale.sysScale()
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

  @SuppressWarnings("NullableProblems")
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
    MyCefAppHandler(@Nullable String[] args) {
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
    }
  }
}

