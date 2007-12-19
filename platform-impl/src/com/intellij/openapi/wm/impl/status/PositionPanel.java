package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

//Made public for Fabrique
public class PositionPanel extends TextPanel implements StatusBarPatch {
  public PositionPanel(StatusBar statusBar) {
    super(false, "#########");

    addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final Project project = getProject();
          if (project == null) return;
          final Editor editor = getEditor(project);
          if (editor == null) return;
          final CommandProcessor processor = CommandProcessor.getInstance();
          processor.executeCommand(
              project, new Runnable(){
              public void run() {
                final GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
                dialog.show();
                IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
              }
            },
              UIBundle.message("go.to.line.command.name"),
            null
          );
        }
      }
    });

    StatusBarTooltipper.install(this, statusBar);
  }

  public JComponent getComponent() {
    return this;
  }
  
  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    if (selected != null) {
      setText(selected.getCaretModel().getLogicalPosition().line + 1 + ":" + (selected.getCaretModel().getLogicalPosition().column + 1));
      return UIBundle.message("go.to.line.command.double.click");
    }
    clear();
    return null;
  }

  public void clear() {
    setText("");
  }

  private static Editor getEditor(final Project project) {
    return FileEditorManager.getInstance(project).getSelectedTextEditor();
  }

  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }
}
