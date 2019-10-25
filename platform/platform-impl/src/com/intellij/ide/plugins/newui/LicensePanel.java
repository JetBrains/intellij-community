// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
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
  private final LinkLabel<Object> myLink = new LinkLabel<>();

  public LicensePanel(boolean tiny) {
    setLayout(new BorderLayout());

    myLink.setIconTextGap(0);
    myLink.setHorizontalTextPosition(SwingConstants.LEFT);

    add(tiny ? PluginManagerConfigurable.setTinyFont(mySubMessage) : mySubMessage, BorderLayout.NORTH);
    add(myPanel);

    myPanel.add(tiny ? PluginManagerConfigurable.setTinyFont(myMessage) : myMessage);
    myPanel.add(tiny ? PluginManagerConfigurable.setTinyFont(myLink) : myLink);

    hideElements();
  }

  public boolean isNotification() {
    return myMessage.getIcon() != null || myMessage.isForegroundSet();
  }

  @Nullable
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

  public void setText(@NotNull String text, boolean warning, boolean errorColor) {
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

    myMessage.setForeground(errorColor ? DialogWrapper.ERROR_FOREGROUND_COLOR : null);
    myMessage.setVisible(true);

    myPanel.setVisible(true);
  }

  public void setTextFromStamp(@NotNull String stamp, @Nullable Date expirationDate) {
    long days = expirationDate == null ? 0 : DateFormatUtil.getDifferenceInDays(new Date(), expirationDate);

    if (stamp.startsWith("eval:")) {
      if (days <= 0) {
        setText("Trial expired.", false, true);
      }
      else {
        setText("Trial expires in " + days + " days.", days < 11, false);
      }
    }
    else if (expirationDate == null) {
      setText("License is active.", false, false);
    }
    else if (days > 30) {
      setText("License is active until " + PluginManagerConfigurable.DATE_FORMAT.format(expirationDate) + ".", false, false);
    }
    else if (days <= 0) {
      setText("License expired.", false, true);
    }
    else {
      setText("License expires in " + days + " days.", days < 11, false);
    }
  }

  public void setLink(@NotNull String text, @NotNull Runnable action, boolean external) {
    myLink.setText(text);
    myLink.setIcon(external ? AllIcons.Ide.External_link_arrow : null);
    myLink.setListener((__, ___) -> action.run(), null);
    myLink.setVisible(true);

    myPanel.setVisible(true);
  }

  public void updateLink(@NotNull String text, boolean async) {
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

  public void showBuyPlugin(@NotNull Supplier<IdeaPluginDescriptor> getPlugin) {
    IdeaPluginDescriptor plugin = getPlugin.get();

    setLink("buy the plugin", () ->
      BrowserUtil.browse("https://plugins.jetbrains.com/purchase-link/" + plugin.getProductCode()), true);

    PluginPriceService.getPrice(plugin, price -> updateLink("buy the plugin from " + price, false), price -> {
      if (plugin == getPlugin.get()) {
        updateLink("buy the plugin from " + price, true);
      }
    });
  }
}