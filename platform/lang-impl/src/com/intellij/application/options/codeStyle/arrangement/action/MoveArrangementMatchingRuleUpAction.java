// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.arrangement.action;

import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesControl;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.IconUtil;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Denis Zhdanov
 */
public class MoveArrangementMatchingRuleUpAction extends AbstractMoveArrangementRuleAction {
  public MoveArrangementMatchingRuleUpAction() {
    getTemplatePresentation().setText(ApplicationBundle.messagePointer("arrangement.action.rule.move.up.text"));
    getTemplatePresentation().setDescription(ApplicationBundle.messagePointer("arrangement.action.rule.move.up.description"));
    getTemplatePresentation().setIcon(IconUtil.getMoveUpIcon());
    setEnabledInModalContext(true);
  }

  @Override
  protected void fillMappings(@NotNull ArrangementMatchingRulesControl control, @NotNull List<int[]> mappings) {
    IntList rows = control.getSelectedModelRows();
    rows.sort(IntComparators.OPPOSITE_COMPARATOR);
    int top = -1;
    for (int i = 0; i < rows.size(); i++) {
      int row = rows.getInt(i);
      if (row == top + 1) {
        mappings.add(new int[] { row, row });
        top++;
      }
      else {
        mappings.add(new int[]{ row, row - 1 });
      }
    } 
  }
}
