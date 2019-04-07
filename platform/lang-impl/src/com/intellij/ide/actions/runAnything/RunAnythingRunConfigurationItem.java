// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.SimpleTextAttributes.STYLE_SMALLER;

public class RunAnythingRunConfigurationItem extends RunAnythingItemBase {
  public static final String RUN_CONFIGURATION_AD_TEXT = RunAnythingUtil.AD_CONTEXT_TEXT + ", " + RunAnythingUtil.AD_DEBUG_TEXT;
  private final ChooseRunConfigurationPopup.ItemWrapper myWrapper;

  public RunAnythingRunConfigurationItem(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper, @Nullable Icon icon) {
    super(wrapper.getText(), icon);
    myWrapper = wrapper;
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected, boolean hasFocus) {
    JPanel component = new JPanel(new BorderLayout());
    Color background = UIUtil.getListBackground(isSelected, hasFocus);

    component.setBackground(background);
    component.setBorder(JBUI.Borders.empty(1, UIUtil.isUnderWin10LookAndFeel() ? 0 : JBUI.scale(UIUtil.getListCellHPadding())));

    Color foreground = UIUtil.getListForeground(isSelected, hasFocus);
    SimpleColoredComponent runConfigComponent = new SimpleColoredComponent();
    runConfigComponent.append(myWrapper.getText(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foreground));
    setupIcon(runConfigComponent, myIcon);
    component.add(runConfigComponent, BorderLayout.WEST);

    ConfigurationType type = myWrapper.getType();
    if (type == null) {
      return component;
    }

    String description = type.getConfigurationTypeDescription();
    if (description == null) {
      return component;
    }

    SimpleColoredComponent descriptionComponent = new SimpleColoredComponent();
    descriptionComponent.append(description, new SimpleTextAttributes(STYLE_SMALLER, foreground));
    component.add(descriptionComponent, BorderLayout.EAST);

    return component;
  }
}