// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunAnythingRunConfigurationItem extends RunAnythingItemBase {
  public static final String RUN_CONFIGURATION_AD_TEXT = RunAnythingUtil.AD_CONTEXT_TEXT + ", " + RunAnythingUtil.AD_DEBUG_TEXT;
  private final ChooseRunConfigurationPopup.ItemWrapper myWrapper;

  public RunAnythingRunConfigurationItem(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper, @Nullable Icon icon) {
    super(wrapper.getText(), icon);
    myWrapper = wrapper;
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    ConfigurationType type = myWrapper.getType();
    String description = null;
    if (type != null) {
      description = type.getConfigurationTypeDescription();
    }
    SimpleColoredComponent component = new SimpleColoredComponent();
    setupIcon(component, myIcon);
    component.append(StringUtil.shortenTextWithEllipsis(myWrapper.getText(), 40, 0));

    appendDescription(component, description);

    return component;
  }
}