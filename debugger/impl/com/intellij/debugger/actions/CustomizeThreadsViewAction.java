package com.intellij.debugger.actions;

import com.intellij.debugger.settings.ThreadsViewConfigurable;
import com.intellij.debugger.settings.ThreadsViewSettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Sep 26, 2003
 * Time: 4:40:12 PM
 */
public class CustomizeThreadsViewAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());
    final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new ThreadsViewConfigurable(ThreadsViewSettings.getInstance()));
    editor.show();
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setText(ActionsBundle.actionText(DebuggerActions.CUSTOMIZE_THREADS_VIEW));
  }
}
