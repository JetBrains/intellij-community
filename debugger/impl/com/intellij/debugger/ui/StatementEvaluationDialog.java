package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.EvaluateAction;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.DimensionService;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 4:28:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class StatementEvaluationDialog extends EvaluationDialog{
  private JPanel myPanel;
  private final Action mySwitchAction = new SwitchAction();
  private static final @NonNls String STATEMENT_EDITOR_DIMENSION_KEY = "#com.intellij.debugger.ui.StatementEvaluationDialog.StatementEditor";
  private static final @NonNls String EVALUATION_PANEL_DIMENSION_KEY = "#com.intellij.debugger.ui.StatementEvaluationDialog.EvaluationPanel";
  private JLabel myLanguageLabel;

  public StatementEvaluationDialog(final Project project, TextWithImports text) {
    super(project, text);
    setTitle(DebuggerBundle.message("evaluate.statement.dialog.title"));
    myPanel = new JPanel(new BorderLayout());

    final Splitter splitter = new Splitter(true);
    splitter.setHonorComponentsMinimumSize(true);

    final JPanel editorPanel = new JPanel(new GridBagLayout());
    myLanguageLabel = new JLabel(DebuggerBundle.message("label.evaluate.dialog.language"));
    myLanguageLabel.setVisible(getCodeFragmentFactoryChooserComponent().isVisible());
    editorPanel.add(myLanguageLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    editorPanel.add(getCodeFragmentFactoryChooserComponent(), new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));

    final JLabel statementsLabel = new JLabel(DebuggerBundle.message("label.evaluation.dialog.statements"));
    editorPanel.add(statementsLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(5, 0, 0, 0), 0, 0));
    editorPanel.add(getStatementEditor(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));

    splitter.setFirstComponent(editorPanel);

    final EvaluationDialog.MyEvaluationPanel evaluationPanel = getEvaluationPanel();
    final JPanel ep = new JPanel(new BorderLayout());
    final JLabel resultLabel = new JLabel(DebuggerBundle.message("label.evaluate.dialog.result"));
    ep.add(resultLabel, BorderLayout.NORTH);
    ep.add(evaluationPanel, BorderLayout.CENTER);
    splitter.setSecondComponent(ep);
    final Dimension statementSize = DimensionService.getInstance().getSize(STATEMENT_EDITOR_DIMENSION_KEY);
    final Dimension evaluationSize = DimensionService.getInstance().getSize(EVALUATION_PANEL_DIMENSION_KEY);
    if (statementSize != null && evaluationSize != null) {
      final float proportion = (float)statementSize.height / (float)(statementSize.height + evaluationSize.height);
      splitter.setProportion(proportion);
    }
    myPanel.add(splitter, BorderLayout.CENTER);

    setDebuggerContext(getDebuggerContext());

    final KeyStroke codeFragment = KeyStroke.getKeyStroke(KeyEvent.VK_E,     KeyEvent.ALT_MASK);
    final KeyStroke resultStroke = KeyStroke.getKeyStroke(KeyEvent.VK_R,     KeyEvent.ALT_MASK);
    final KeyStroke altEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_MASK);

    final JRootPane rootPane = getRootPane();
    final AnAction toStatementAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getStatementEditor().requestFocus();
      }
    };
    toStatementAction.registerCustomShortcutSet(new CustomShortcutSet(codeFragment), rootPane);
    addDisposeRunnable(new Runnable() {
      public void run() {
        toStatementAction.unregisterCustomShortcutSet(rootPane);
      }
    });

    final AnAction toEvaluationAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        getEvaluationPanel().getWatchTree().requestFocus();
      }
    };
    toEvaluationAction.registerCustomShortcutSet(new CustomShortcutSet(resultStroke), rootPane);
    addDisposeRunnable(new Runnable() {
      public void run() {
        toEvaluationAction.unregisterCustomShortcutSet(rootPane);
      }
    });

    final AnAction okAction = new AnAction() {
      public void actionPerformed(AnActionEvent e) {
        doOKAction();
      }
    };
    okAction.registerCustomShortcutSet(new CustomShortcutSet(altEnter), rootPane);
    addDisposeRunnable(new Runnable() {
      public void run() {
        okAction.unregisterCustomShortcutSet(rootPane);
      }
    });

    final DebuggerEditorImpl editor = getEditor();
    final DocumentAdapter docListener = new DocumentAdapter() {
      public void documentChanged(final DocumentEvent e) {
        DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
          public void run() {
            updateSwitchButton(e.getDocument());
          }
        });
      }
    };
    editor.addDocumentListener(docListener);
    addDisposeRunnable(new Runnable() {
      public void run() {
        editor.removeDocumentListener(docListener);
      }
    });

    this.init();
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    super.setDebuggerContext(context);
    if (myLanguageLabel != null) {
      myLanguageLabel.setVisible(getCodeFragmentFactoryChooserComponent().isVisible());
    }
  }

  private void updateSwitchButton(Document document) {
    PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(document);
    PsiElement[] children = psiFile.getChildren();
    int nonWhite = 0;
    for (PsiElement child : children) {
      if (!(child instanceof PsiWhiteSpace)) {
        nonWhite++;
        if (nonWhite > 1) {
          mySwitchAction.setEnabled(false);
          return;
        }
      }
    }

    mySwitchAction.setEnabled(true);
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), getCancelAction(), mySwitchAction };
  }

  protected DebuggerEditorImpl createEditor() {
    return new DebuggerStatementEditor(getProject(), PositionUtil.getContextElement(getDebuggerContext()), "evaluation");
  }

  public void dispose() {
    try {
      final DebuggerEditorImpl editor = getEditor();
      final DimensionService dimensionService = DimensionService.getInstance();
      dimensionService.setSize(STATEMENT_EDITOR_DIMENSION_KEY, editor.getSize(null));
      dimensionService.setSize(EVALUATION_PANEL_DIMENSION_KEY, getEvaluationPanel().getSize());
    }
    finally {
      super.dispose();
    }
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private DebuggerStatementEditor getStatementEditor() {
    return (DebuggerStatementEditor)getEditor();
  }

  private class SwitchAction extends AbstractAction {
    public SwitchAction() {
      putValue(Action.NAME, DebuggerBundle.message("action.evaluate.statement.dialog.switch.mode.description"));
    }

    public void actionPerformed(ActionEvent e) {
      final TextWithImports text = getEditor().getText();
      doCancelAction();
      DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
        public void run() {
          EvaluateAction.showEvaluationDialog(getProject(), text, DebuggerSettings.EVALUATE_EXPRESSION);
        }
      });
    }
  }


}
