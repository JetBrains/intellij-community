// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Urls;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class JcefWhatsNewPageProvider implements WhatsNewPageProvider {
  private static final Logger LOG = Logger.getInstance(JcefWhatsNewPageProvider.class);

  @Override
  public boolean isAvailable() {
    return JBCefApp.isSupported() && !ScreenReader.isActive();
  }

  @Override
  public void openWhatsNewPage(@NotNull Project project,
                               @NotNull String url,
                               boolean includePlatformData,
                               @Nullable HTMLEditorProvider.JsQueryHandler queryHandler) {
    if (!isAvailable()) {
      throw new IllegalStateException("JCEF is not supported on this system");
    }

    var darkTheme = StartupUiUtil.INSTANCE.isDarkTheme();
    var request = HTMLEditorProvider.Request.url(Urls.newFromEncoded(url).addParameters(getRequestParameters(includePlatformData)).toExternalForm());

    try (var stream = JcefWhatsNewPageProvider.class.getResourceAsStream("whatsNewTimeoutText.html")) {
      if (stream != null) {
        request.withTimeoutHtml(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                                  .replace("__THEME__", darkTheme ? "theme-dark" : "")
                                  .replace("__TITLE__", IdeBundle.message("whats.new.timeout.title"))
                                  .replace("__MESSAGE__", IdeBundle.message("whats.new.timeout.message"))
                                  .replace("__ACTION__", IdeBundle.message("whats.new.timeout.action", url)));
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    request.withQueryHandler(queryHandler);

    var title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().getFullProductName());
    HTMLEditorProvider.openEditorWithoutBlocking(project, title, request);
  }

  private static Map<String, String> getRequestParameters(boolean includePlatformData) {
    var parameters = new LinkedHashMap<String, String>();

    parameters.put("var", "embed");

    var theme = StartupUiUtil.INSTANCE.isDarkTheme() ? "dark" : "light";
    if (ExperimentalUI.isNewUI()) theme += "-new-ui";
    parameters.put("theme", theme);

    parameters.put("lang", Locale.getDefault().toLanguageTag().toLowerCase(Locale.ENGLISH));

    if (includePlatformData) {
      var os = OS.CURRENT == OS.Windows ? "windows" : OS.CURRENT == OS.macOS ? "mac" : OS.CURRENT == OS.Linux ? "linux" : null;
      var arch = CpuArch.CURRENT == CpuArch.X86_64 ? "" : CpuArch.CURRENT == CpuArch.ARM64 ? "ARM64" : null;
      if (os != null && arch != null) {
        parameters.put("platform", os + arch);
        parameters.put("product", ApplicationInfo.getInstance().getBuild().getProductCode());
      }
    }

    return parameters;
  }
}
