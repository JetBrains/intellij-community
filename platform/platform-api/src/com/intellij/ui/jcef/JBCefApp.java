// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.handler.CefAppHandlerAdapter;

/**
 * @author tav
 */
public class JBCefApp {
  private static final CefApp ourCefApp;

  static {
    CefSettings settings = new CefSettings();
    settings.windowless_rendering_enabled = false;
    settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_ERROR;
    if (SystemInfo.isMac) {
      CefApp.startup();
      String JCEF_FRAMEWORKS_PATH = System.getProperty("java.home") + "/Frameworks";
      CefApp.addAppHandler(new CefAppHandlerAdapter(new String[] {
        "--framework-dir-path=" + JCEF_FRAMEWORKS_PATH + "/Chromium Embedded Framework.framework",
        "--browser-subprocess-path=" + JCEF_FRAMEWORKS_PATH + "/jcef Helper.app/Contents/MacOS/jcef Helper",
        "--disable-in-process-stack-traces"
      }) {});
    }
    else if (SystemInfo.isLinux) {
      CefApp.startup();
      String JCEF_PATH = System.getProperty("java.home") + "/lib";
      settings.resources_dir_path = JCEF_PATH;
      settings.locales_dir_path = JCEF_PATH + "/locales";
      settings.browser_subprocess_path = JCEF_PATH + "/jcef_helper";
    }
    ourCefApp = CefApp.getInstance(settings);
    Disposer.register(ApplicationManager.getApplication(), () -> {
      ourCefApp.dispose();
    });
  }

  public static CefClient getCefClient() {
    return ourCefApp.createClient();
  }
}

