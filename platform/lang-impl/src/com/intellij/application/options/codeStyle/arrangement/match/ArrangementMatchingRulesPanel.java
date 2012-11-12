/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.ArrangementNodeDisplayManager;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.util.TitleWithToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 10/30/12 5:28 PM
 */
public class ArrangementMatchingRulesPanel extends JPanel implements DataProvider {

  @NotNull private final ArrangementMatchingRulesControl myControl;

  public ArrangementMatchingRulesPanel(@NotNull ArrangementNodeDisplayManager displayManager,
                                       @NotNull ArrangementColorsProvider colorsProvider,
                                       @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    super(new GridBagLayout());
    TitleWithToolbar top = new TitleWithToolbar(
      ApplicationBundle.message("arrangement.settings.section.match"),
      ArrangementConstants.ACTION_GROUP_RULE_EDITOR_TOOLBAR,
      ArrangementConstants.RULE_EDITOR_TOOLBAR_PLACE
    );

    JBScrollPane scrollPane = new JBScrollPane();
    final JViewport viewport = scrollPane.getViewport();
    ArrangementMatchingRulesControl.RepresentationCallback callback = new ArrangementMatchingRulesControl.RepresentationCallback() {
      @Override
      public void ensureVisible(@NotNull Rectangle r) {
        Rectangle visibleRect = viewport.getViewRect();
        if (r.y <= visibleRect.y) {
          return;
        }

        int excessiveHeight = r.y + r.height - (visibleRect.y + visibleRect.height);
        if (excessiveHeight <= 0) {
          return;
        }

        int verticalShift = Math.min(r.y - visibleRect.y, excessiveHeight);
        if (verticalShift > 0) {
          viewport.setViewPosition(new Point(visibleRect.x, visibleRect.y + verticalShift));
        }
      }
    };
    myControl = new ArrangementMatchingRulesControl(displayManager, colorsProvider, settingsFilter, callback);
    scrollPane.setViewportView(myControl);

    add(top, new GridBag().coverLine().fillCellHorizontally().weightx(1));
    add(scrollPane, new GridBag().fillCell().weightx(1).weighty(1));
  }

  @NotNull
  public List<StdArrangementMatchRule> getRules() {
    return myControl.getRules();
  }
  
  public void setRules(@Nullable List<StdArrangementMatchRule> rules) {
    myControl.setRules(rules);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (ArrangementConstants.MATCHING_RULES_CONTROL_KEY.is(dataId)) {
      return myControl;
    }
    return null;
  }
}
