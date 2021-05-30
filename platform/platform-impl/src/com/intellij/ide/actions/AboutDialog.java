// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
  private List<String> myInfo = new ArrayList<>();

  public AboutDialog(Project project) {
    this(project, false);
  }

  public AboutDialog(Project project, boolean showDebugInfo) {
    super(project, false);
    String appName = ApplicationNamesInfo.getInstance().getFullProductName();
    //setSize(600, 400);
    setResizable(false);
    //noinspection HardCodedStringLiteral
    setTitle("About " + appName);

    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    Icon appIcon = AppUIUtil.loadApplicationIcon(ScaleContext.create(), 100);
    Box box = getText();
    JLabel icon = new JLabel(appIcon);
    icon.setVerticalAlignment(SwingConstants.TOP);
    icon.setBorder(JBUI.Borders.empty(0, 10, 0, 40));
    box.setBorder(JBUI.Borders.emptyRight(20));

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
        try {
          CopyPasteManager.getInstance().setContents(new StringSelection(getExtendedAboutText()));
        }
        catch (Exception ignore) { }
        close(OK_EXIT_CODE);
      }
    };
  }

  private String getExtendedAboutText() {
    return StringUtil.join(myInfo, "\n") + AboutPopup.getExtraInfo();
  }

  @NonNls
  private Box getText() {
    Box html = Box.createVerticalBox();
    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    String appName = appInfo.getFullApplicationName();
    String edition = ApplicationNamesInfo.getInstance().getEditionName();
    if (edition != null) appName += " (" + edition + ")";
    html.add(new JBLabel(appName).withFont(JBFont.h2()));
    myInfo.add(appName);

    String buildInfo = IdeBundle.message("about.box.build.number", appInfo.getBuild().asString());
    Date timestamp = appInfo.getBuildDate().getTime();
    if (appInfo.getBuild().isSnapshot()) {
      buildInfo += IdeBundle.message("about.box.build.date.time", DateFormatUtil.formatAboutDialogDate(timestamp), new SimpleDateFormat("HH:mm").format(timestamp));
    }
    else {
      buildInfo += IdeBundle.message("about.box.build.date", DateFormatUtil.formatAboutDialogDate(timestamp));
    }
    html.add(new JBLabel(buildInfo));
    html.add(Box.createVerticalStrut(20));
    myInfo.add(buildInfo);

    LicensingFacade la = LicensingFacade.getInstance();
    if (la != null) {
      final String licensedTo = la.getLicensedToMessage();
      if (licensedTo != null) {
        html.add(new JBLabel(licensedTo).withFont(JBFont.regular().asBold()));
        myInfo.add(licensedTo);
      }
      for (String message : la.getLicenseRestrictionsMessages()) {
        html.add(new JBLabel(message));
        myInfo.add(message);
      }
    }

    html.add(Box.createVerticalStrut(20));

    Properties properties = System.getProperties();
    String javaVersion = properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"));
    String arch = properties.getProperty("os.arch", "");
    html.add(new JBLabel(IdeBundle.message("about.box.jre", javaVersion, arch)));

    String vmVersion = properties.getProperty("java.vm.name", "unknown");
    String vmVendor = properties.getProperty("java.vendor", "unknown");
    html.add(new JBLabel(IdeBundle.message("about.box.vm", vmVersion, vmVendor)));

    html.add(Box.createVerticalStrut(20));


    //HyperlinkLabel label = new HyperlinkLabel();
    //label.setTextWithHyperlink(IdeBundle.message("about.box.powered.by.open.source"));
    //label.addHyperlinkListener(new HyperlinkAdapter() {
    //  @Override
    //  protected void hyperlinkActivated(HyperlinkEvent e) {
    //    AboutPopup.showOpenSoftwareSources(ObjectUtils.notNull(AboutPopup.loadThirdPartyLibraries(), ""));
    //  }
    //});
    //html.add(label);
    JBLabel link = new JBLabel(IdeBundle.message("about.box.open.source.software"));
    link.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);

    //html.add(JBUI.Panels.simplePanel()
    //  .addToLeft(new JBLabel(IdeBundle.message("about.box.powered.by") + " "))
    //  .addToCenter(link));
    html.add(new JBLabel("Powered by open-source software"));
    html.add(new JBLabel(AboutPopup.getCopyrightText()));
    html.add(Box.createVerticalStrut(20));
    html.add(Box.createVerticalStrut(20));
    //SimpleColoredComponent text = new SimpleColoredComponent();
    //text.append(IdeBundle.message("about.box.powered.by"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    //text.append(IdeBundle.message("about.box.open.source.software"), SimpleTextAttributes.LINK_ATTRIBUTES);
    //html.add(text);
    return html;
  }
}
