// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.AboutPopupDescriptionProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class AboutDialog extends DialogWrapper {

  /**
   * Use paragraph to support copy/paste multilines
   */
  private static final String EOL = "<p>";

  private final List<String> myInfo = new ArrayList<>();

  public AboutDialog(@Nullable Project project) {
    super(project, false);
    String appName = ApplicationNamesInfo.getInstance().getFullProductName();
    setResizable(false);
    setTitle(IdeBundle.message("about.popup.about.app", appName));

    init();
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent result = super.createSouthPanel();

    // Register copy action on buttons panel only, because it conflicts with copyable labels in center panel
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
  protected @Nullable JComponent createCenterPanel() {
    Icon appIcon = AppUIUtil.loadApplicationIcon(ScaleContext.create(), 60);
    Box box = getText();
    JLabel icon = new JLabel(appIcon);
    icon.setVerticalAlignment(SwingConstants.TOP);
    icon.setBorder(JBUI.Borders.empty(20, 12, 0, 24));
    box.setBorder(JBUI.Borders.empty(20,0,0,20));

    return JBUI.Panels.simplePanel()
      .addToLeft(icon)
      .addToCenter(box);
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new OkAction() {
      {
        putValue(Action.NAME, IdeBundle.message("button.copy.no.mnemonic"));
        putValue(Action.SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"));
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
    catch (Exception ignore) { }
  }

  public String getExtendedAboutText() {
    return StringUtil.join(myInfo, "\n") + "\n" + AboutPopup.getExtraInfo();
  }

  @SuppressWarnings("DuplicatedCode")
  private Box getText() {
    Box box = Box.createVerticalBox();
    List<String> lines = new ArrayList<>();
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String appName = appInfo.getFullApplicationName(); //NON-NLS
    String edition = ApplicationNamesInfo.getInstance().getEditionName();
    if (edition != null) appName += " (" + edition + ")";
    box.add(label(appName, JBFont.h3().asBold()));
    box.add(Box.createVerticalStrut(10));
    myInfo.add(appName);

    String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
    String buildInfoNonLocalized = MessageFormat.format("Build #{0}", appInfo.getBuild().asString());
    Date timestamp = appInfo.getBuildDate().getTime();
    if (appInfo.getBuild().isSnapshot()) {
      String time = new SimpleDateFormat("HH:mm").format(timestamp);
      buildInfo += IdeBundle.message("about.box.build.date.time", NlsMessages.formatDateLong(timestamp), time);
      buildInfoNonLocalized += MessageFormat.format(", built on {0} at {1}",
                                                    DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(timestamp), time);
    }
    else {
      buildInfo += IdeBundle.message("about.box.build.date", NlsMessages.formatDateLong(timestamp));
      buildInfoNonLocalized += MessageFormat.format(", built on {0}",
                                                    DateFormat.getDateInstance(DateFormat.LONG, Locale.US).format(timestamp));
    }
    lines.add(buildInfo);
    lines.add("");
    myInfo.add(buildInfoNonLocalized);

    LicensingFacade la = LicensingFacade.getInstance();
    if (la != null) {
      final String licensedTo = la.getLicensedToMessage(); //NON-NLS
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
    String jreInfo = IdeBundle.message("about.box.jre", javaVersion, arch);
    lines.add(jreInfo);
    myInfo.add(MessageFormat.format("Runtime version: {0} {1}", javaVersion, arch));

    String vmVersion = properties.getProperty("java.vm.name", "unknown");
    String vmVendor = properties.getProperty("java.vendor", "unknown");
    String vmVendorInfo = IdeBundle.message("about.box.vm", vmVersion, vmVendor);
    lines.add(vmVendorInfo);
    lines.add("");
    myInfo.add(MessageFormat.format("VM: {0} by {1}", vmVersion, vmVendor));

    //Print extra information from plugins
    ExtensionPointName<AboutPopupDescriptionProvider> ep = new ExtensionPointName<>("com.intellij.aboutPopupDescriptionProvider");
    for (AboutPopupDescriptionProvider aboutInfoProvider : ep.getExtensions()) {
      String description = aboutInfoProvider.getDescription(); //NON-NLS
      if (description != null) {
        lines.add(description);
        lines.add("");
      }
    }

    String text = String.join(EOL, lines); //NON-NLS
    box.add(label(text, getDefaultTextFont()));
    addEmptyLine(box);

    //Link to open-source projects
    HyperlinkLabel openSourceSoftware = new HyperlinkLabel();
    //noinspection DialogTitleCapitalization
    openSourceSoftware.setTextWithHyperlink(IdeBundle.message("about.box.powered.by.open.source"));
    openSourceSoftware.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        AboutPopup.showOpenSoftwareSources(ObjectUtils.notNull(AboutPopup.loadThirdPartyLibraries(), ""));
      }
    });
    openSourceSoftware.setFont(getDefaultTextFont());
    JBLabel poweredBy = new JBLabel(IdeBundle.message("about.box.powered.by") + " ").withFont(getDefaultTextFont());
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(openSourceSoftware).addToLeft(poweredBy);
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    box.add(panel);

    //Copyright
    box.add(label(AboutPopup.getCopyrightText(), getDefaultTextFont()));
    addEmptyLine(box);

    return box;
  }

  private static JBFont getDefaultTextFont() {
    return JBFont.medium();
  }

  private static void addEmptyLine(Box box) {
    box.add(Box.createVerticalStrut(18));
  }

  private static JLabel label(@NlsContexts.Label String text, JBFont font) {
    JBLabel label = new JBLabel(text).withFont(font);
    label.setCopyable(true);
    return label;
  }
}
