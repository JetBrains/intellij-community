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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.actions.EvaluateActionHandler;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ExpressionEvaluationDialog extends EvaluationDialog {

  public ExpressionEvaluationDialog(Project project, TextWithImports defaultExpression) {
    super(project, defaultExpression);
    setTitle(DebuggerBundle.message("evaluate.expression.dialog.title"));

    final KeyStroke expressionStroke = KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK);
    final KeyStroke resultStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.ALT_MASK);

    final JRootPane rootPane = getRootPane();

    final AnAction anAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getExpressionCombo().requestFocus();
      }
    };
    anAction.registerCustomShortcutSet(new CustomShortcutSet(expressionStroke), rootPane);
    addDisposeRunnable(new Runnable() {
      public void run() {
        anAction.unregisterCustomShortcutSet(rootPane);
      }
    });

    final AnAction anAction2 = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getEvaluationPanel().getWatchTree().requestFocus();
      }
    };
    anAction2.registerCustomShortcutSet(new CustomShortcutSet(resultStroke), rootPane);
    addDisposeRunnable(new Runnable() {
      public void run() {
        anAction2.unregisterCustomShortcutSet(rootPane);
      }
    });

    init();
  }

  protected DebuggerExpressionComboBox createEditor(final CodeFragmentFactory factory) {
    return new DebuggerExpressionComboBox(getProject(), PositionUtil.getContextElement(getDebuggerContext()), "evaluation", factory);
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    final JPanel exprPanel = new JPanel(new BorderLayout());
    exprPanel.add(new JLabel(DebuggerBundle.message("label.evaluate.dialog.expression")), BorderLayout.WEST);
    exprPanel.add(getExpressionCombo(), BorderLayout.CENTER);
    final JLabel help = new JLabel(
      String.format("Press Enter to Evaluate or %s+Enter to evaluate and add to the Watches", SystemInfo.isMac ? "Command" : "Control"), SwingConstants.RIGHT);
    help.setBorder(IdeBorderFactory.createEmptyBorder(2,0,6,0));
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, help);
    help.setForeground(UIUtil.getInactiveTextColor());
    exprPanel.add(help, BorderLayout.SOUTH);


    final JPanel resultPanel = new JPanel(new BorderLayout());
    //resultPanel.add(new JLabel(DebuggerBundle.message("label.evaluate.dialog.result")), BorderLayout.NORTH);
    resultPanel.add(getEvaluationPanel(), BorderLayout.CENTER);

    panel.add(exprPanel, BorderLayout.NORTH);
    panel.add(resultPanel, BorderLayout.CENTER);

    return panel;
  }

  protected void initDialogData(TextWithImports text) {
    super.initDialogData(text);
    getExpressionCombo().selectAll();
  }

  private DebuggerExpressionComboBox getExpressionCombo() {
    return (DebuggerExpressionComboBox)getEditor();
  }

  protected Action[] createActions() {
    return new Action[] { getOKAction(), getCancelAction(), new SwitchAction(), getHelpAction() } ;
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new OkAction(){
      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        final int mask = SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK;
        if ((e.getModifiers() & mask) != 0) {
          addCurrentExpressionToWatches();
        }
      }
    };
  }

  private void addCurrentExpressionToWatches() {
    final DebuggerSessionTab tab = DebuggerPanelsManager.getInstance(getProject()).getSessionTab();
    if (tab != null) {
      final TextWithImports evaluate = getCodeToEvaluate();
      if (evaluate != null) {
        tab.getWatchPanel().getWatchTree().addWatch(evaluate);
      }
    }
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EVALUATE);
  }

  private class SwitchAction extends AbstractAction {
    public SwitchAction() {
      putValue(Action.NAME, DebuggerBundle.message("action.evaluate.expression.dialog.switch.mode.description"));
    }

    public void actionPerformed(ActionEvent e) {
      final TextWithImports text = getEditor().getText();
      doCancelAction();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          EvaluateActionHandler.showEvaluationDialog(getProject(), text, DebuggerSettings.EVALUATE_FRAGMENT);
        }
      });
    }
  }

}
