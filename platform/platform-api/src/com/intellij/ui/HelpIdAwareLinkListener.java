// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

@Service
@ApiStatus.Experimental
public final class HelpIdAwareLinkListener extends BrowserHyperlinkListener {

  @NonNls private static final String HELP_LINK_MARKER = "helpInstance:";
  @NonNls private static final String URL_TEMPLATE = "https://www.jetbrains.com/help/%s?%s&utm_version=%s";

  @NotNull
  public static HelpIdAwareLinkListener getInstance() {
    return ApplicationManager.getApplication().getService(HelpIdAwareLinkListener.class);
  }

  @Override
  protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {

    final String description = e.getDescription();

    if (description != null && description.trim().startsWith(HELP_LINK_MARKER)) {

      final String wouldBeHelpId = description.trim().substring(HELP_LINK_MARKER.length()).trim();

      if (StringUtil.isNotEmpty(wouldBeHelpId)) {
        final ApplicationNamesInfo nameInfo = ApplicationNamesInfo.getInstance();
        final String editionName = nameInfo.getEditionName();


        final String productName = StringUtil.toLowerCase(nameInfo.getProductName());
        final String productWebPath = switch (productName) {
          case "rubymine", "ruby" -> "ruby";
          case "intellij idea", "idea" -> "idea";
          case "goland" -> "go";
          case "appcode" -> "objc";
          case "pycharm" -> editionName != null && "edu".equals(StringUtil.toLowerCase(editionName)) ? "pycharm-edu" : "pycharm";
          default -> productName;
        };

        BrowserUtil.browse(String.format(URL_TEMPLATE, productWebPath, wouldBeHelpId, ApplicationInfo.getInstance().getShortVersion()));
        return;
      }
    }
    super.hyperlinkActivated(e);
  }
}