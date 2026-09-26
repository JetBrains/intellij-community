// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.TooltipTitle;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Dimension;
import java.net.URL;

public class ContextHelpLabel extends JBLabel {
  private final HelpTooltip tooltip;

  public ContextHelpLabel(@Nls String label, @Tooltip String description) {
    super(label);
    this.tooltip = new HelpTooltip().setDescription(description);
    initTooltip();
  }

  private ContextHelpLabel(@NotNull HelpTooltip tooltip) {
    super(AllIcons.General.ContextHelp);
    this.tooltip = tooltip;
  }

  private void initTooltip() {
    tooltip.setNeverHideOnTimeout(true).setLocation(HelpTooltip.Alignment.HELP_BUTTON);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    tooltip.installOn(this);
  }

  @Override
  public void removeNotify() {
    HelpTooltip.dispose(this);
    super.removeNotify();
  }

  public static @NotNull ContextHelpLabel create(@Tooltip @NotNull String description) {
    ContextHelpLabel label = new ContextHelpLabel(new HelpTooltip().setDescription(description));
    label.initTooltip();
    return label;
  }

  public static @NotNull ContextHelpLabel create(@TooltipTitle @NotNull String title, @Tooltip @NotNull String description) {
    ContextHelpLabel label = new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title));
    label.initTooltip();
    return label;
  }

  public static @NotNull ContextHelpLabel createWithLink(@TooltipTitle @Nullable String title,
                                                         @Tooltip @NotNull String description,
                                                         @LinkLabel @NotNull String linkText,
                                                         @NotNull Runnable linkAction) {
    return createWithLink(title, description, linkText, false, linkAction);
  }

  public static @NotNull ContextHelpLabel createWithLink(@TooltipTitle @Nullable String title,
                                                         @Tooltip @NotNull String description,
                                                         @LinkLabel @NotNull String linkText,
                                                         boolean linkIsExternal,
                                                         @NotNull Runnable linkAction) {
    ContextHelpLabel label =
      new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title).setLink(linkText, linkAction, linkIsExternal));
    label.initTooltip();
    return label;
  }

  public static @NotNull ContextHelpLabel createWithBrowserLink(@TooltipTitle @Nullable String title,
                                                                @Tooltip @NotNull String description,
                                                                @LinkLabel @NotNull String linkText,
                                                                @NotNull URL url) {
    ContextHelpLabel label =
      new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title).setBrowserLink(linkText, url));
    label.initTooltip();
    return label;
  }

  public static @NotNull ContextHelpLabel createFromTooltip(HelpTooltip helpTooltip) {
    return new ContextHelpLabel(helpTooltip);
  }

  @Override
  public void setPreferredSize(Dimension size) { }
}
