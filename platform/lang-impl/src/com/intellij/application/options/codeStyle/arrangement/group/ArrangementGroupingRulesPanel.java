// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.group;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.TitleWithToolbar;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ArrangementGroupingRulesPanel extends JPanel implements UiDataProvider {

  private final @NotNull ArrangementGroupingRulesControl myControl;

  public ArrangementGroupingRulesPanel(@NotNull ArrangementStandardSettingsManager settingsManager,
                                       @NotNull ArrangementColorsProvider colorsProvider)
  {
    super(new GridBagLayout());

    myControl = new ArrangementGroupingRulesControl(settingsManager, colorsProvider);

    TitleWithToolbar top = new TitleWithToolbar(
      ApplicationBundle.message("arrangement.settings.section.groups"),
      ArrangementConstants.ACTION_GROUP_GROUPING_RULES_CONTROL_TOOLBAR,
      ArrangementConstants.GROUPING_RULES_CONTROL_TOOLBAR_PLACE,
      myControl
    );
    
    add(top, new GridBag().coverLine().fillCellHorizontally().weightx(1));
    add(myControl, new GridBag().fillCell().weightx(1).weighty(1).insets(0, ArrangementConstants.HORIZONTAL_PADDING, 0, 0));
  }

  public void setRules(@Nullable List<? extends ArrangementGroupingRule> rules) {
    myControl.setRules(rules);
  }
  
  public @NotNull List<ArrangementGroupingRule> getRules() {
    return myControl.getRules();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(ArrangementGroupingRulesControl.KEY, myControl);
  }
}
