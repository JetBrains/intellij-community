// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class SideEffectWarningDialog extends DialogWrapper {
  private final PsiVariable myVariable;
  private final @NlsSafe String myBeforeText;
  private final @NlsSafe String myAfterText;
  private final boolean myCanCopeWithSideEffects;
  private AbstractAction myRemoveAllAction;
  private AbstractAction myCancelAllAction;

  public SideEffectWarningDialog(Project project, boolean canBeParent, PsiVariable variable, @NlsSafe String beforeText, @NlsSafe String afterText, boolean canCopeWithSideEffects) {
    super(project, canBeParent);
    myVariable = variable;
    myBeforeText = beforeText;
    myAfterText = afterText;
    myCanCopeWithSideEffects = canCopeWithSideEffects;
    setTitle(QuickFixBundle.message("side.effects.warning.dialog.title"));
    init();

  }

  @Override
  protected Action @NotNull [] createActions() {
    List<AbstractAction> actions = new ArrayList<>();
    myRemoveAllAction = new AbstractAction() {
      {
        UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.remove"), this);
        putValue(DEFAULT_ACTION, this);
      }

      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        close(RemoveUnusedVariableUtil.RemoveMode.DELETE_ALL.ordinal());
      }

    };
    actions.add(myRemoveAllAction);
    if (myCanCopeWithSideEffects) {
      AbstractAction makeStmtAction = new AbstractAction() {
        {
          UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.transform"), this);
          if (SystemInfo.isMac) {
            putValue(FOCUSED_ACTION, this);
          }
        }

        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          close(RemoveUnusedVariableUtil.RemoveMode.MAKE_STATEMENT.ordinal());
        }
      };
      actions.add(makeStmtAction);
    }
    myCancelAllAction = new AbstractAction() {
      {
        UIUtil.setActionNameAndMnemonic(QuickFixBundle.message("side.effect.action.cancel"), this);
      }

      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        doCancelAction();
      }

    };
    actions.add(myCancelAllAction);
    return actions.toArray(new Action[0]);
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
    close(RemoveUnusedVariableUtil.RemoveMode.CANCEL.ordinal());
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final String text = sideEffectsDescription();
    final JLabel label = new JBLabel(text);
    label.setIcon(Messages.getWarningIcon());
    panel.add(label, BorderLayout.NORTH);
    return panel;
  }

  @Nls
  protected String getFormatString() {
    return JavaBundle.partialMessage("side.effects.pattern.message", 3);
  }

  protected @NlsContexts.Label String sideEffectsDescription() {
    if (myCanCopeWithSideEffects) {
      return MessageFormat.format(getFormatString(),
                                  JavaBundle.message("side.effects.expressions.assigned.to.the.variable", myVariable.getName()),
                                  myVariable.getType().getPresentableText() + " " + myVariable.getName() + " = " + myBeforeText,
                                  myAfterText);
    }
    else {
      return QuickFixBundle.message("side.effect.message1", myVariable.getName());
    }
  }
}
