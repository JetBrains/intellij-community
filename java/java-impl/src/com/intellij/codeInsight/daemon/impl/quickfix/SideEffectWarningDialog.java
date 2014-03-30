/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public class SideEffectWarningDialog extends DialogWrapper {
  private final PsiVariable myVariable;
  private final String myBeforeText;
  private final String myAfterText;
  private final boolean myCanCopeWithSideEffects;
  private AbstractAction myRemoveAllAction;
  private AbstractAction myCancelAllAction;

  public SideEffectWarningDialog(Project project, boolean canBeParent, PsiVariable variable, String beforeText, String afterText, boolean canCopeWithSideEffects) {
    super(project, canBeParent);
    myVariable = variable;
    myBeforeText = beforeText;
    myAfterText = afterText;
    myCanCopeWithSideEffects = canCopeWithSideEffects;
    setTitle(QuickFixBundle.message("side.effects.warning.dialog.title"));
    init();

  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<AbstractAction> actions = new ArrayList<AbstractAction>();
    myRemoveAllAction = new AbstractAction() {
      {
        UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.remove"), this);
        putValue(DEFAULT_ACTION, this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        close(RemoveUnusedVariableUtil.DELETE_ALL);
      }

    };
    actions.add(myRemoveAllAction);
    if (myCanCopeWithSideEffects) {
      AbstractAction makeStmtAction = new AbstractAction() {
        {
          UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.transform"), this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
          close(RemoveUnusedVariableUtil.MAKE_STATEMENT);
        }
      };
      actions.add(makeStmtAction);
    }
    myCancelAllAction = new AbstractAction() {
      {
        UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.cancel"), this);
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        doCancelAction();
      }

    };
    actions.add(myCancelAllAction);
    return actions.toArray(new Action[actions.size()]);
  }

  @NotNull
  @Override
  protected Action getCancelAction() {
    return myCancelAllAction;
  }

  @NotNull
  @Override
  protected Action getOKAction() {
    return myRemoveAllAction;
  }

  @Override
  public void doCancelAction() {
    close(RemoveUnusedVariableUtil.CANCEL);
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final String text = sideEffectsDescription();
    final JLabel label = new JLabel(text);
    label.setIcon(Messages.getWarningIcon());
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }

  protected String sideEffectsDescription() {
    if (myCanCopeWithSideEffects) {
      return QuickFixBundle.message("side.effect.message2",
                                    myVariable.getName(),
                                    myVariable.getType().getPresentableText(),
                                    myBeforeText,
                                    myAfterText);
    }
    else {
      return QuickFixBundle.message("side.effect.message1", myVariable.getName());
    }
  }
}
