// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
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
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * @author Konstantin Bulenkov
 */
public class AboutDialog extends DialogWrapper {
  private final List<String> myInfo = new ArrayList<>();

  public AboutDialog(Project project) {
    this(project, false);
  }

  public AboutDialog(Project project, boolean showDebugInfo) {
    super(project, false);
    String appName = ApplicationNamesInfo.getInstance().getFullProductName();
    setResizable(false);
    setTitle(IdeBundle.message("about.popup.about.app", appName));

    init();

    new DumbAwareAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        copyAboutInfoToClipboard();
        close(OK_EXIT_CODE);
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("meta C", "control C"), getContentPanel(), getDisposable());
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
    myCancelAction = new DialogWrapperAction(IdeBundle.message("button.copy")) {
      {
        putValue(Action.SHORT_DESCRIPTION, IdeBundle.message("description.copy.text.to.clipboard"));
      }

      @Override
      protected void doAction(ActionEvent e) {
        copyAboutInfoToClipboard();
        close(OK_EXIT_CODE);
      }
    };
  }

  private void copyAboutInfoToClipboard() {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(getExtendedAboutText()));
    }
    catch (Exception ignore) { }
  }

  private String getExtendedAboutText() {
    return StringUtil.join(myInfo, "\n") + "\n" + AboutPopup.getExtraInfo();
  }

  @NonNls
  private Box getText() {
    Box lines = Box.createVerticalBox();
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String appName = appInfo.getFullApplicationName(); //NON-NLS
    String edition = ApplicationNamesInfo.getInstance().getEditionName();
    if (edition != null) appName += " (" + edition + ")";
    addLine(lines, appName, JBFont.h3().asBold());
    lines.add(Box.createVerticalStrut(10));

    String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
    Date timestamp = appInfo.getBuildDate().getTime();
    if (appInfo.getBuild().isSnapshot()) {
      buildInfo += IdeBundle.message("about.box.build.date.time", DateFormatUtil.formatAboutDialogDate(timestamp), new SimpleDateFormat("HH:mm").format(timestamp));
    }
    else {
      buildInfo += IdeBundle.message("about.box.build.date", DateFormatUtil.formatAboutDialogDate(timestamp));
    }
    addLine(lines, buildInfo);
    addEmptyLine(lines);

    LicensingFacade la = LicensingFacade.getInstance();
    if (la != null) {
      final String licensedTo = la.getLicensedToMessage(); //NON-NLS
      if (licensedTo != null) {
        addLine(lines, licensedTo);
      }

      la.getLicenseRestrictionsMessages()
        .forEach(text -> addLine(lines, text)); //NON-NLS
    }
    addEmptyLine(lines);

    Properties properties = System.getProperties();
    String javaVersion = properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
    String arch = properties.getProperty("os.arch", "");
    String jreInfo = IdeBundle.message("about.box.jre", javaVersion, arch);
    addLine(lines, jreInfo);

    String vmVersion = properties.getProperty("java.vm.name", "unknown");
    String vmVendor = properties.getProperty("java.vendor", "unknown");
    String vmVendorInfo = IdeBundle.message("about.box.vm", vmVersion, vmVendor);
    addLine(lines, vmVendorInfo);
    addEmptyLine(lines);

    HyperlinkLabel openSourceSoftware = new HyperlinkLabel();
    //noinspection DialogTitleCapitalization
    openSourceSoftware.setTextWithHyperlink(IdeBundle.message("about.box.powered.by.open.source"));
    openSourceSoftware.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        AboutPopup.showOpenSoftwareSources(ObjectUtils.notNull(AboutPopup.loadThirdPartyLibraries(), ""));
      }
    });
    openSourceSoftware.setFont(getDefaultTextFont());
    JBLabel poweredBy = new JBLabel(IdeBundle.message("about.box.powered.by") + " ").withFont(getDefaultTextFont());
    BorderLayoutPanel panel = JBUI.Panels.simplePanel(openSourceSoftware).addToLeft(poweredBy);
    panel.setAlignmentX(Component.LEFT_ALIGNMENT);
    lines.add(panel);

    addLineWithoutLog(lines, AboutPopup.getCopyrightText());
    addEmptyLine(lines);

    return lines;
  }

  private static JBFont getDefaultTextFont() {
    return JBFont.medium();
  }

  private static void addEmptyLine(Box box) {
    box.add(Box.createVerticalStrut(18));
  }

  private void addLine(JComponent panel, @NlsContexts.Label String text, JBFont font) {
    addLine(panel, text, font, true);
  }
  private void addLine(JComponent panel, @NlsContexts.Label String text, JBFont font, boolean log) {
    JBLabel label = new JBLabel(text).withFont(font);
    panel.add(label);

    if (log) {
      myInfo.add(text);
    }
  }

  private void addLineWithoutLog(JComponent panel, @NlsContexts.Label String text) {
    addLine(panel, text, getDefaultTextFont(), false);
  }

  private void addLine(JComponent panel, @NlsContexts.Label String text) {
    addLine(panel, text, getDefaultTextFont());
  }
}
