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
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class ExpressionEvaluationDialog extends EvaluationDialog {
  private JLabel myLanguageLabel;

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
    final JPanel panel = new JPanel(new GridBagLayout());

    myLanguageLabel = new JLabel(DebuggerBundle.message("label.evaluate.dialog.language"));
    myLanguageLabel.setVisible(getCodeFragmentFactoryChooserComponent().isVisible());
    panel.add(myLanguageLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    panel.add(getCodeFragmentFactoryChooserComponent(), new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));

    final JLabel expressionLabel = new JLabel(DebuggerBundle.message("label.evaluate.dialog.expression"));
    panel.add(expressionLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    panel.add(getExpressionCombo(), new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 0, 0), 0, 0));

    final JLabel resultLabel = new JLabel(DebuggerBundle.message("label.evaluate.dialog.result"));
    panel.add(resultLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    panel.add(getEvaluationPanel(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(5, 0, 0, 0), 0, 0));

    return panel;
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    super.setDebuggerContext(context);
    if (myLanguageLabel != null) {
      myLanguageLabel.setVisible(getCodeFragmentFactoryChooserComponent().isVisible());
    }
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
