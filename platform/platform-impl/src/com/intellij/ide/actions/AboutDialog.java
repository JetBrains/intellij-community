// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.AboutPopupDescriptionProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.AppMode;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.platform.buildData.productInfo.CustomProperty;
import com.intellij.platform.buildData.productInfo.CustomPropertyNames;
import com.intellij.platform.ide.productInfo.IdeProductInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.system.OS;
import com.intellij.util.ui.*;
import com.jetbrains.cef.JCefAppConfig;
import com.jetbrains.cef.JCefVersionDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNullElse;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public final class AboutDialog extends DialogWrapper {
  private static final ExtensionPointName<AboutPopupDescriptionProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.aboutPopupDescriptionProvider");

  /**
   * See {@link org.jetbrains.intellij.build.impl.DistributionJARsBuilderKt#createBuildThirdPartyLibraryListJob}.
   */
  private static final String THIRD_PARTY_LIBRARIES_FILE = "license/third-party-libraries.html";

  private final List<String> myInfo = new ArrayList<>();

  public AboutDialog(@Nullable Project project) {
    super(project, false);
    String appName = getFullNameForAboutDialog();
    setResizable(false);
    setTitle(IdeBundle.message("about.popup.about.app", appName));
    setShouldUseWriteIntentReadAction(false);

    init();
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent result = super.createSouthPanel();

    // Registering the copy action only on the buttons panel, because it conflicts with copyable labels in the center panel
    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        copyAboutInfoToClipboard();
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("meta C", "control C"), result, getDisposable());

    return result;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    Icon appIcon = AppUIUtil.loadApplicationIcon(ScaleContext.create(), 60);
    Box box = getText();
    JLabel icon = new JLabel(appIcon);
    icon.setVerticalAlignment(SwingConstants.TOP);
    icon.setBorder(JBUI.Borders.empty(20, 12, 0, 24));
    box.setBorder(JBUI.Borders.empty(20, 0, 0, 20));

    return JBUI.Panels.simplePanel()
      .addToLeft(icon)
      .addToCenter(box);
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new OkAction() {
      {
        putValue(NAME, IdeBundle.message("button.copy.and.close"));
        putValue(SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"));
      }

      @Override
      protected void doAction(ActionEvent e) {
        copyAboutInfoToClipboard();
        close(OK_EXIT_CODE);
      }
    };
    myCancelAction.putValue(Action.NAME, IdeBundle.message("action.close"));
  }

  private void copyAboutInfoToClipboard() {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(getExtendedAboutText()));
    }
    catch (Exception ignore) {
    }
  }

  private @NotNull Box getText() {
    JBBox box = JBBox.createVerticalBox();
    List<String> lines = new ArrayList<>();
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    @NlsSafe String appName = appInfo.getFullApplicationName();
    String edition = ApplicationNamesInfo.getInstance().getEditionName();
    if (edition != null) appName += " (" + edition + ")";
    box.add(label(appName, JBFont.h3().asBold()));
    box.add(Box.createVerticalStrut(10));
    myInfo.add(appName);

    @NotNull Pair<String, String> result = getBuildInfo(appInfo);
    lines.add(result.first);
    lines.add("");
    myInfo.add(result.second);
    CustomProperty revision = ContainerUtil.find(
      IdeProductInfo.getInstance().getCurrentProductInfo()
        .getCustomProperties(), o -> CustomPropertyNames.GIT_REVISION.equals(o.getKey()));
    if (revision != null) {
      myInfo.add("Source revision: " + revision.getValue());
    }

    LicensingFacade la = LicensingFacade.getInstance();
    if (la != null) {
      String licensedTo = la.getLicensedToMessage();
      if (licensedTo != null) {
        lines.add(licensedTo);
        myInfo.add(licensedTo);
      }

      lines.addAll(la.getLicenseRestrictionsMessages());
      myInfo.addAll(la.getLicenseRestrictionsMessages());
    }
    lines.add("");

    Properties properties = System.getProperties();
    String javaVersion = properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
    String arch = properties.getProperty("os.arch", "");
    String jcefSuffix = getJcefVersion();
    if (!jcefSuffix.isEmpty()) {
      jcefSuffix = " (" + jcefSuffix + ")";
    }
    String jreInfo = IdeBundle.message("about.box.jre", javaVersion, arch) + jcefSuffix;
    lines.add(jreInfo);
    myInfo.add(MessageFormat.format("Runtime version: {0} {1}", javaVersion, arch) + jcefSuffix);

    String vmVersion = properties.getProperty("java.vm.name", "unknown");
    String vmVendor = properties.getProperty("java.vendor", "unknown");
    String vmVendorInfo = IdeBundle.message("about.box.vm", vmVersion, vmVendor);
    lines.add(vmVendorInfo);
    lines.add("");
    myInfo.add(MessageFormat.format("VM: {0} by {1}", vmVersion, vmVendor));

    // Print extra information from plugins
    for (AboutPopupDescriptionProvider aboutInfoProvider : EP_NAME.getExtensionList()) {
      String description = aboutInfoProvider.getDescription();
      if (description != null) {
        lines.add(description);
        lines.add("");
      }
    }

    @NlsSafe String text = String.join("<p>", lines);  // joining with paragraph separators for better-looking copied text
    box.add(label(text, getDefaultTextFont()));
    addEmptyLine(box);

    //Link to open-source projects
    HyperlinkLabel openSourceSoftware = hyperlinkLabel(IdeBundle.message("about.box.powered.by"));
    openSourceSoftware.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        showOssInfo(box);
      }
    });
    box.add(openSourceSoftware);

    //Copyright
    var year = Integer.toString(LocalDate.now().getYear());
    var copyright = hyperlinkLabel(IdeBundle.message("about.box.copyright", appInfo.getCopyrightStart(), year, appInfo.getCompanyName()));
    copyright.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        BrowserUtil.browse(appInfo.getCompanyURL());
      }
    });
    box.add(copyright);
    addEmptyLine(box);

    return box;
  }

  public static @NotNull Pair<String, String> getBuildInfo(@NotNull ApplicationInfo appInfo) {
    String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
    String buildInfoNonLocalized = MessageFormat.format("Build #{0}", appInfo.getBuild().asString());
    Date buildDate = appInfo.getBuildDate().getTime();
    String formattedBuildDate = DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(buildDate);

    if (AppMode.isDevServer()) {
      // Dev mode build date is not accurate, so we don't show it to avoid confusion
      buildInfo += IdeBundle.message("about.box.build.date.omitted.in.dev.build.mode");
      buildInfoNonLocalized += ", build date omitted in Dev build mode";
    }
    else if (appInfo.getBuild().isSnapshot()) {
      String buildTime = new SimpleDateFormat("HH:mm").format(buildDate);
      buildInfo += IdeBundle.message("about.box.build.date.time", NlsMessages.formatDateLong(buildDate), buildTime);
      buildInfoNonLocalized += MessageFormat.format(", built on {0} at {1}", formattedBuildDate, buildTime);
    }
    else {
      buildInfo += IdeBundle.message("about.box.build.date", NlsMessages.formatDateLong(buildDate));
      buildInfoNonLocalized += MessageFormat.format(", built on {0}", formattedBuildDate);
    }

    return Pair.create(buildInfo, buildInfoNonLocalized);
  }

  private static JBFont getDefaultTextFont() {
    return JBFont.medium();
  }

  private static void addEmptyLine(@NotNull Box box) {
    box.add(Box.createVerticalStrut(18));
  }

  private static @NotNull JLabel label(@NlsContexts.Label @NotNull String text, JBFont font) {
    var label = new JBLabel(text).withFont(font);
    label.setCopyable(true);
    return label;
  }

  private static @NotNull HyperlinkLabel hyperlinkLabel(@NlsContexts.LinkLabel @NotNull String textWithLink) {
    var hyperlinkLabel = new HyperlinkLabel();
    hyperlinkLabel.setTextWithHyperlink(textWithLink);
    hyperlinkLabel.setFont(getDefaultTextFont());
    return hyperlinkLabel;
  }

  public @NotNull String getExtendedAboutText() {
    var text = new StringBuilder();

    myInfo.forEach(s -> text.append(s).append('\n'));

    text.append("Toolkit: ").append(Toolkit.getDefaultToolkit().getClass().getName()).append("\n");

    text.append(OS.CURRENT.name()).append(' ').append(OS.CURRENT.version()).append('\n');

    for (var aboutInfoProvider : EP_NAME.getExtensionList()) {
      var description = aboutInfoProvider.getExtendedDescription();
      if (description != null) {
        text.append(description).append('\n');
      }
    }

    String garbageCollectors = ManagementFactory.getGarbageCollectorMXBeans()
      .stream()
      .map(GarbageCollectorMXBean::getName)
      .collect(Collectors.joining(", "));

    text.append("GC: ").append(garbageCollectors).append('\n');
    text.append("Memory: ").append(Runtime.getRuntime().maxMemory() / FileUtilRt.MEGABYTE).append("M\n");
    text.append("Cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');

    if (UIUtil.isMetalRendering()) {
      text.append("Metal Rendering is ON\n");
    }

    var changedValues = Registry.getAll().stream().filter(RegistryValue::isChangedFromDefault).toList();
    if (!changedValues.isEmpty()) {
      text.append("Registry:\n");
      changedValues.forEach(v -> text.append("  ").append(v.getKey()).append('=').append(v.asString()).append('\n'));
    }

    var extraPlugins = PluginManagerCore.getLoadedPlugins().stream().filter(p -> !p.isBundled()).toList();
    if (!extraPlugins.isEmpty()) {
      text.append("Non-Bundled Plugins:\n");
      extraPlugins.forEach(p -> text.append("  ").append(p.getPluginId().getIdString()).append(" (").append(p.getVersion()).append(")\n"));
    }

    if (PlatformUtils.isIntelliJ()) {
      var kotlinPlugin = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"));
      if (kotlinPlugin != null) {
        text.append("Kotlin: ").append(kotlinPlugin.getVersion()).append('\n');
      }
    }

    if (SystemInfo.isUnix && !SystemInfo.isMac) {
      text.append("Current Desktop: ").append(requireNonNullElse(System.getenv("XDG_CURRENT_DESKTOP"), "Undefined")).append('\n');
    }

    return text.toString();
  }

  private static void showOssInfo(JComponent component) {
    @NlsSafe String licenseText;
    try {
      var title = IdeBundle.message("progress.third.party.software");
      licenseText = ProgressManager.getInstance().run(new Task.WithResult<String, IOException>(null, component, title, true) {
        @Override
        protected String compute(@NotNull ProgressIndicator indicator) throws IOException {
          return DiskQueryRelay.compute(() -> {
            var content = Files.readString(Path.of(PathManager.getHomePath(), THIRD_PARTY_LIBRARIES_FILE));
            var matcher = Pattern.compile("(\\d+)px").matcher(content);
            var sb = new StringBuilder();
            while (matcher.find()) {
              matcher.appendReplacement(sb, JBUIScale.scale(Integer.parseInt(matcher.group(1))) + "px");
            }
            matcher.appendTail(sb);
            content = sb.toString();
            if (StartupUiUtil.INSTANCE.isDarkTheme()) {
              content = content.replace("779dbd", "5676a0");
            }
            return content;
          });
        }
      });
    }
    catch (IOException e) {
      Logger.getInstance(AboutDialog.class).error(e);
      return;
    }

    var dialog = new DialogWrapper(true) {
      {
        init();
        setAutoAdjustable(false);
        setOKButtonText(CommonBundle.message("close.action.name"));
      }

      @Override
      protected @NotNull JComponent createCenterPanel() {
        var viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        viewer.setFocusable(true);
        viewer.addHyperlinkListener(new BrowserHyperlinkListener());
        viewer.setText(licenseText);

        var styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
        styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, \"Helvetica Neue\", Helvetica, Arial, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        styleSheet.addRule("body {font-size:" + JBUIScale.scaleFontSize((float)14) + "pt;}");

        viewer.setCaretPosition(0);
        viewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));

        var scrollPane = new JBScrollPane(viewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);

        var centerPanel = new JPanel(new BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5)));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        return centerPanel;
      }

      @Override
      protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction()};
      }
    };

    dialog.setTitle(IdeBundle.message("dialog.title.third.party.software",
                                      getFullNameForAboutDialog(),
                                      ApplicationInfo.getInstance().getFullVersion()));
    dialog.setSize(JBUIScale.scale(750), JBUIScale.scale(650));
    dialog.show();
  }

  private static @NotNull String getFullNameForAboutDialog() {
    if (!PlatformUtils.isJetBrainsClient()) return ApplicationNamesInfo.getInstance().getFullProductName();
    return IdeBundle.message("dialog.message.jetbrains.client.for.ide", ApplicationNamesInfo.getInstance().getFullProductName());
  }

  private static @NotNull String getJcefVersion() {
    if (JBCefApp.isSupported()) {
      try {
        JCefVersionDetails version = JCefAppConfig.getVersionDetails();
        return IdeBundle.message("about.box.jcef", version.cefVersion.major, version.cefVersion.api, version.cefVersion.patch);
      }
      catch (JCefVersionDetails.VersionUnavailableException ignored) {
      }
    }
    return "";
  }
}
