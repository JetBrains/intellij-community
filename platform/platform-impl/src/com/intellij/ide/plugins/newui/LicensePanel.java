// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.function.Supplier;

/**
 * @author Alexander Lobas
 */
public class LicensePanel extends NonOpaquePanel {
  private final JLabel mySubMessage = new JLabel();
  private final JPanel myPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(5)));
  private final JLabel myMessage = new JLabel();
  private final ActionLink myLink = new ActionLink();
  private Runnable myLinkRunnable;

  public LicensePanel(boolean tiny) {
    setLayout(new BorderLayout());

    add(tiny ? PluginManagerConfigurable.setTinyFont(mySubMessage) : mySubMessage, BorderLayout.NORTH);
    add(myPanel);

    myPanel.add(tiny ? PluginManagerConfigurable.setTinyFont(myMessage) : myMessage);
    myPanel.add(tiny ? PluginManagerConfigurable.setTinyFont(myLink) : myLink);

    myLink.addActionListener(e -> myLinkRunnable.run());

    hideElements();
  }

  public boolean isNotification() {
    return myMessage.getIcon() != null || myMessage.isForegroundSet();
  }

  @Nullable
  @NlsSafe
  public String getMessage() {
    String text = myMessage.getText();
    if (mySubMessage.isVisible()) {
      return mySubMessage.getText() + "\n" + text;
    }
    if (StringUtil.endsWithChar(text, '.')) {
      return text.substring(0, text.length() - 1);
    }
    return text;
  }

  public void setText(@NotNull @Nls String text, boolean warning, boolean errorColor) {
    int separator = text.indexOf('\n');

    if (separator == -1) {
      myMessage.setText(text);
      myMessage.setIcon(warning ? AllIcons.General.Warning : null);
    }
    else {
      mySubMessage.setText(text.substring(0, separator));
      mySubMessage.setIcon(warning ? AllIcons.General.Warning : null);
      mySubMessage.setVisible(true);

      myMessage.setText(text.substring(separator + 1));
      myMessage.setIcon(warning ? EmptyIcon.ICON_16 : null);
    }

    myMessage.setForeground(errorColor ? UIUtil.getErrorForeground() : null);
    myMessage.setVisible(true);

    myPanel.setVisible(true);
  }

  public void setTextFromStamp(@NotNull String stamp, @Nullable Date expirationDate) {
    long days = expirationDate == null ? 0 : DateFormatUtil.getDifferenceInDays(new Date(), expirationDate);

    if (stamp.startsWith("eval:")) {
      if (days <= 0) {
        setText(IdeBundle.message("trial.expired"), false, true);
      }
      else {
        setText(IdeBundle.message("plugins.configurable.trial.expires.in.0.days", days), days < 11, false);
      }
    }
    else if (expirationDate == null) {
      setText(IdeBundle.message("plugins.configurable.license.is.active"), false, false);
    }
    else if (days > 30) {
      setText(IdeBundle
                .message("plugins.configurable.license.is.active.until.0", PluginManagerConfigurable.DATE_FORMAT.format(expirationDate)), false, false);
    }
    else if (days <= 0) {
      setText(IdeBundle.message("plugins.configurable.license.expired"), false, true);
    }
    else {
      setText(IdeBundle.message("plugins.configurable.license.expires.in.0.days", days), days < 11, false);
    }
  }

  public void setLink(@NotNull @Nls String text, @NotNull Runnable action, boolean external) {
    myLinkRunnable = action;

    myLink.setText(text);
    if (external) {
      myLink.setExternalLinkIcon();
    }
    else {
      myLink.setIcon(null);
    }
    myLink.setVisible(true);

    myPanel.setVisible(true);
  }

  public void updateLink(@NotNull @Nls String text, boolean async) {
    myLink.setText(text);
    if (async) {
      myPanel.doLayout();
    }
  }

  public void hideWithChildren() {
    hideElements();
    setVisible(false);
  }

  private void hideElements() {
    mySubMessage.setVisible(false);
    myMessage.setVisible(false);
    myLink.setVisible(false);
    myPanel.setVisible(false);
  }

  public void showBuyPlugin(@NotNull Supplier<? extends IdeaPluginDescriptor> getPlugin) {
    IdeaPluginDescriptor plugin = getPlugin.get();

    setLink(IdeBundle.message("plugins.configurable.buy.the.plugin"), () ->
      BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://plugins.jetbrains.com/purchase-link/" + plugin.getProductCode())), true);

    PluginPriceService.getPrice(plugin, price -> updateLink(IdeBundle.message("plugins.configurable.buy.the.plugin.from.0", price), false), price -> {
      if (plugin == getPlugin.get()) {
        updateLink(IdeBundle.message("plugins.configurable.buy.the.plugin.from.0", price), true);
      }
    });
  }

  public static boolean isEA2Product(@Nullable String productCodeOrPluginId) {
    return productCodeOrPluginId != null &&
      LicensingFacade.getInstance() != null &&
      ArrayUtil.contains(productCodeOrPluginId, "DPN", "DC", "DPA", "PDB", "PWS", "PGO", "PPS", "PPC", "PRB", "PSW", "Pythonid");
  }
}