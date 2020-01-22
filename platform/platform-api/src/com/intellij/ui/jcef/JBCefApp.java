// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import org.cef.CefApp;
import org.cef.CefSettings;
import org.cef.handler.CefAppHandlerAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import java.awt.*;

/**
 * @author tav
 */
@ApiStatus.Experimental
public abstract class JBCefApp {
  private final static JBCefApp INSTANCE;
  private final CefApp ourCefApp;

  static {
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
      INSTANCE = null;
      assert false : "JBCefApp platform initialization failed";
    }
  }

  private JBCefApp() {
    CefSettings settings = new CefSettings();
    settings.windowless_rendering_enabled = false;
    settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_ERROR;
    Color bg = JBColor.background();
    settings.background_color = settings.new ColorType(bg.getAlpha(), bg.getRed(), bg.getGreen(), bg.getBlue());
    CefApp.startup();
    //noinspection AbstractMethodCallInConstructor
    init(settings);
    ourCefApp = CefApp.getInstance(settings);
    Disposer.register(ApplicationManager.getApplication(), () -> {
      ourCefApp.dispose();
    });
  }

  public static JBCefApp getInstance() {
    return INSTANCE;
  }

  protected abstract void init(@NotNull CefSettings settings);

  private static class JBCefAppMac extends JBCefApp {
    @Override
    protected void init(@NotNull CefSettings settings) {
      String JCEF_FRAMEWORKS_PATH = System.getProperty("java.home") + "/Frameworks";
      CefApp.addAppHandler(new CefAppHandlerAdapter(new String[] {
        "--framework-dir-path=" + JCEF_FRAMEWORKS_PATH + "/Chromium Embedded Framework.framework",
        "--browser-subprocess-path=" + JCEF_FRAMEWORKS_PATH + "/jcef Helper.app/Contents/MacOS/jcef Helper",
        "--disable-in-process-stack-traces"
      }) {});
    }
  }

  private static class JBCefAppWindows extends JBCefApp {
    @Override
    protected void init(@NotNull CefSettings settings) {
      String JCEF_PATH = System.getProperty("java.home") + "/bin";
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";
    }
  }

  private static class JBCefAppLinux extends JBCefApp {
    @Override
    protected void init(@NotNull CefSettings settings) {
      String JCEF_PATH = System.getProperty("java.home") + "/lib";
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";

      CefApp.addAppHandler(new CefAppHandlerAdapter(new String[] {
        "--force-device-scale-factor=" + JBUIScale.sysScale()
      }) {});
    }
  }

  public JBCefClient createClient() {
    return new JBCefClient(ourCefApp.createClient());
  }
}

