// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.dsl.listCellRenderer.LcrUtilsKt;
import com.intellij.ui.render.RendererPanelsUtils;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.SimpleTextAttributes.*;

public class RunAnythingItemBase extends RunAnythingItem {
  private final @NotNull @NlsSafe String myCommand;
  protected final @Nullable Icon myIcon;

  public RunAnythingItemBase(@NotNull @NlsSafe String command, @Nullable Icon icon) {
    myCommand = command;
    myIcon = icon;
  }

  @Override
  public @NotNull String getCommand() {
    return myCommand;
  }

  public @Nullable @Nls String getDescription() {
    return null;
  }

  @Override
  public @NotNull Component createComponent(@Nullable String pattern, boolean isSelected, boolean hasFocus) {
    JPanel component = new JPanel(new BorderLayout());
    Color background = UIUtil.getListBackground(isSelected, true);
    component.setBackground(background);

    SimpleColoredComponent textComponent = new SimpleColoredComponent();
    SpeedSearchUtil.appendColoredFragmentForMatcher(StringUtil.notNullize(getCommand()),
                                                    textComponent,
                                                    REGULAR_ATTRIBUTES,
                                                    RunAnythingGroup.RUN_ANYTHING_MATCHER_BUILDER.fun(pattern).build(),
                                                    background,
                                                    isSelected);
    component.add(textComponent, BorderLayout.WEST);
    textComponent.appendTextPadding(20);
    setupIcon(textComponent, myIcon);
    addDescription(component, isSelected);

    return component;
  }

  private void addDescription(@NotNull JPanel panel, boolean isSelected) {
    String description = getDescription();
    if (description == null) {
      return;
    }

    SimpleColoredComponent descriptionComponent = new SimpleColoredComponent();
    descriptionComponent.append(description, getDescriptionAttributes(isSelected));
    descriptionComponent.setTextAlign(SwingConstants.RIGHT);
    panel.add(descriptionComponent, BorderLayout.CENTER);
  }

  public void setupIcon(@NotNull SimpleColoredComponent component, @Nullable Icon icon) {
    component.setIcon(ObjectUtils.notNull(icon, EmptyIcon.ICON_16));
    if (ExperimentalUI.isNewUI()) {
      component.setIconTextGap(RendererPanelsUtils.getIconTextGap());
      LcrUtilsKt.stripHorizontalInsets(component);
    } else {
      component.setIpad(JBUI.insets(0, 10, 0, 0));
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RunAnythingItemBase base = (RunAnythingItemBase)o;

    if (!myCommand.equals(base.myCommand)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myCommand.hashCode();
  }

  protected static void appendDescription(@NotNull SimpleColoredComponent component,
                                          @NlsContexts.DetailedDescription @Nullable String description,
                                          @NotNull Color foreground) {
    if (description != null) {
      SimpleTextAttributes smallAttributes = new SimpleTextAttributes(STYLE_SMALLER, foreground);
      component.append(StringUtil.shortenTextWithEllipsis(description, 40, 0), smallAttributes);
      component.appendTextPadding(660, SwingConstants.RIGHT);
    }
  }

  private static @NotNull SimpleTextAttributes getDescriptionAttributes(boolean isSelected) {
    return new SimpleTextAttributes(STYLE_PLAIN, isSelected ? NamedColorUtil.getListSelectionForeground(true) : NamedColorUtil.getInactiveTextColor());
  }
}
