/*
 * Class EvaluateAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.ExpressionEvaluationDialog;
import com.intellij.debugger.ui.StatementEvaluationDialog;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

public class EvaluateAction extends DebuggerAction {
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    final Project project = event.getData(DataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    DebuggerContextImpl context = getDebuggerContext(event.getDataContext());

    boolean toEnable = false;

    if(context != null) {
      DebuggerSession debuggerSession = context.getDebuggerSession();

      toEnable = debuggerSession != null && debuggerSession.isPaused();
    }

    presentation.setEnabled(toEnable);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else {
      presentation.setVisible(true);
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final DataContext dataContext = e.getDataContext();
    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(dataContext);

    if(project == null || context == null) {
      return;
    }

    final Editor editor = e.getData(DataKeys.EDITOR);

    TextWithImports editorText = DebuggerUtilsEx.getEditorText(editor);
    if (editorText == null) {
      final DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(dataContext);
      final String actionName = e.getPresentation().getText();

      if (selectedNode != null && selectedNode.getDescriptor() instanceof ValueDescriptorImpl) {
        context.getDebugProcess().getManagerThread().invokeLater(new DebuggerContextCommandImpl(context) {
          public void threadAction() {
            try {
              final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(selectedNode, context);
              DebuggerInvocationUtil.invokeLater(project, new Runnable() {
                public void run() {
                  showEvaluationDialog(project, evaluationText);
                }
              });
            }
            catch (final EvaluateException e1) {
              DebuggerInvocationUtil.invokeLater(project, new Runnable() {
                public void run() {
                  Messages.showErrorDialog(project, e1.getMessage(), actionName);
                }
              });
            }
          }

          protected void commandCancelled() {
            DebuggerInvocationUtil.invokeLater(project, new Runnable() {
              public void run() {
                if(selectedNode.getDescriptor() instanceof WatchItemDescriptor) {
                  try {
                    TextWithImports editorText = DebuggerTreeNodeExpression.createEvaluationText(selectedNode, context);
                    showEvaluationDialog(project, editorText);
                  }
                  catch (EvaluateException e1) {
                    Messages.showErrorDialog(project, e1.getMessage(), actionName);
                  }
                }
              }
            });
          }
        });
        return;
      }
    }

    showEvaluationDialog(project, editorText);
  }

  public static void showEvaluationDialog(Project project, TextWithImports defaultExpression, String dialogType) {
    if(defaultExpression == null) {
      defaultExpression = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "");
    }

    DebuggerSettings.getInstance().EVALUATION_DIALOG_TYPE = dialogType;
    final DialogWrapper dialog;
    if(DebuggerSettings.EVALUATE_FRAGMENT.equals(dialogType)) {
      dialog = new StatementEvaluationDialog(project, defaultExpression);
    }
    else {
      dialog = new ExpressionEvaluationDialog(project, defaultExpression);
    }

    dialog.show();
  }

  public static void showEvaluationDialog(Project project, TextWithImports text) {
    showEvaluationDialog(project, text, DebuggerSettings.getInstance().EVALUATION_DIALOG_TYPE);
  }
}
