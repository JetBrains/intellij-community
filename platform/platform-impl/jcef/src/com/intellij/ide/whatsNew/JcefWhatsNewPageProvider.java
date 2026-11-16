// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.util.Urls;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JcefWhatsNewPageProvider implements WhatsNewPageProvider {
  private static final Logger LOG = Logger.getInstance(JcefWhatsNewPageProvider.class);

  @Override
  public boolean isAvailable() {
    return JBCefApp.isSupported();
  }

  @Override
  public void openWhatsNewPage(
    @NotNull Project project,
    @NotNull String url,
    boolean includePlatformData,
    @Nullable HTMLEditorProvider.JsQueryHandler queryHandler
  ) {
    var parameters = getRequestParameters(includePlatformData, project);
    project.getService(JcefWhatsNewPageOpening.class).openAfterRegionCheck(parameters.containsKey("code"),
      showAirPromo -> openWhatsNewPage(project, url, parameters, queryHandler, showAirPromo));
  }

  private static void openWhatsNewPage(@NotNull Project project,
                                      @NotNull String url,
                                      @NotNull Map<String, String> parameters,
                                      @Nullable HTMLEditorProvider.JsQueryHandler queryHandler,
                                      boolean showAirPromo) {
    var request = HTMLEditorProvider.Request.url(
      Urls.newFromEncoded(url)
        .addParameters(parameters)
        .toExternalForm()
    );

    try (var stream = JcefWhatsNewPageProvider.class.getResourceAsStream("whatsNewTimeoutText.html")) {
      if (stream != null) {
        var darkTheme = StartupUiUtil.INSTANCE.isDarkTheme();
        request.withTimeoutHtml(new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .replace("__THEME__", darkTheme ? "theme-dark" : "")
          .replace("__TITLE__", IdeBundle.message("whats.new.timeout.title"))
          .replace("__MESSAGE__", IdeBundle.message("whats.new.timeout.message"))
          .replace("__ACTION__", IdeBundle.message("whats.new.timeout.action", url))
        );
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    // The page can redirect to the web site, for example from a short link.
    var urls = ExternalProductResourceUrls.getInstance();
    var webSiteUrl = urls instanceof BaseJetBrainsExternalProductResourceUrls jetBrainsUrls ? jetBrainsUrls.getBaseWebSiteUrl().toExternalForm() : null;
    var promoQueryHandler = WhatsNewAirPromoQueryHandler.create(
      new WhatsNewAirPromoPage(project, WelcomeScreenProjectProvider.isWelcomeScreenProject(project)),
      showAirPromo, queryHandler);
    setQueryHandler(request, promoQueryHandler, trustedOrigins(url, webSiteUrl));

    var title = IdeBundle.message("update.whats.new", ApplicationNamesInfo.getInstance().getFullProductName());
    HTMLEditorProvider.openEditorWithoutBlocking(project, title, request);

    WhatsNewUsagesCollector.pageDisplayed(project);
  }

  /**
   * Sets the query handler of the request for the pages of the origins. Without an origin the request gets no handler,
   * because a URL request without trusted origins lets the main frame of any origin call it.
   */
  @VisibleForTesting
  static void setQueryHandler(@NotNull HTMLEditorProvider.Request request,
                              @Nullable HTMLEditorProvider.JsQueryHandler queryHandler,
                              @NotNull List<String> origins) {
    if (queryHandler == null) return;
    if (origins.isEmpty()) {
      LOG.warn("The What's New page has no trusted origin, so it gets no query handler");
      return;
    }
    request.withQueryHandler(queryHandler).withTrustedOrigins(origins.toArray(String[]::new));
  }

  /**
   * Returns the origins whose pages can call the query handler: the origin of the URL of the page and of the web site.
   */
  @VisibleForTesting
  static @NotNull List<String> trustedOrigins(@NotNull String url, @Nullable String webSiteUrl) {
    var origins = new LinkedHashSet<String>();
    for (String it : webSiteUrl == null ? List.of(url) : List.of(url, webSiteUrl)) {
      String origin = originOf(it);
      if (origin != null) origins.add(origin);
    }
    return List.copyOf(origins);
  }

  private static @Nullable String originOf(@NotNull String url) {
    try {
      URI uri = new URI(url);
      if (uri.getScheme() == null || uri.getHost() == null) return null;
      String origin = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) +
                      (uri.getPort() < 0 ? "" : ":" + uri.getPort());
      if (HTMLEditorProvider.Request.isTrustedOrigin(origin)) return origin;
    }
    catch (URISyntaxException ignored) {
    }
    LOG.warn("Cannot get the origin of " + url);
    return null;
  }

  private static Map<String, String> getRequestParameters(boolean includePlatformData, Project project) {
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
      }

      parameters.put("product", ApplicationInfo.getInstance().getBuild().getProductCode());

      if (PluginManagerCore.isLoaded(PluginId.getId("com.intellij.air"))) {
        parameters.put("code", WelcomeScreenProjectProvider.isWelcomeScreenProject(project) ? "welcome-plugin" : "standard-plugin");
      }
    }

    return parameters;
  }
}
