/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import java.awt.*;

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
    initTooltip();
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

  @NotNull
  public static ContextHelpLabel create(@Tooltip @NotNull String description) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description));
  }

  @NotNull
  public static ContextHelpLabel create(@TooltipTitle @NotNull String title, @Tooltip @NotNull String description) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title));
  }

  @NotNull
  public static ContextHelpLabel createWithLink(@TooltipTitle @Nullable String title,
                                                @Tooltip @NotNull String description,
                                                @LinkLabel @NotNull String linkText,
                                                @NotNull Runnable linkAction) {
    return createWithLink(title, description, linkText, false, linkAction);
  }

  @NotNull
  public static ContextHelpLabel createWithLink(@TooltipTitle @Nullable String title,
                                                @Tooltip @NotNull String description,
                                                @LinkLabel @NotNull String linkText,
                                                boolean linkIsExternal,
                                                @NotNull Runnable linkAction) {
    return new ContextHelpLabel(new HelpTooltip().setDescription(description).setTitle(title).setLink(linkText, linkAction, linkIsExternal));
  }

  @Override
  public void setPreferredSize(Dimension size) { }
}
