package ru.compscicenter.edide.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import ru.compscicenter.edide.StudyEditor;
import ru.compscicenter.edide.StudyTaskManager;
import ru.compscicenter.edide.StudyUtils;
import ru.compscicenter.edide.course.Course;
import ru.compscicenter.edide.course.TaskFile;
import ru.compscicenter.edide.course.Window;

import java.io.File;

/**
 * author: liana
 * data: 7/21/14.
 */

public class ShowHintAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      DocumentationManager documentationManager = DocumentationManager.getInstance(project);
      DocumentationComponent component = new DocumentationComponent(documentationManager);
      Editor selectedEditor = StudyEditor.getSelectedEditor(project);
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      if (openedFile != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(e.getProject());
        TaskFile taskFile = taskManager.getTaskFile(openedFile);
        PsiFile file = PsiManager.getInstance(project).findFile(openedFile);
        if (file != null) {
          LogicalPosition pos = selectedEditor.getCaretModel().getLogicalPosition();
          Window window = taskFile.getTaskWindow(selectedEditor, pos);
          if (window != null) {
            String hint = window.getHint();
            File resourceFile = new File(taskManager.getCourse().getResourcePath());
            File resourceRoot = resourceFile.getParentFile();
            if (resourceRoot != null && resourceRoot.exists()) {
              File hintsDir = new File (resourceRoot, Course.HINTS_DIR);
              if (hintsDir.exists()) {
                String hintText = StudyUtils.getFileText(hintsDir.getAbsolutePath(), hint, true);
                if (hintText != null) {
                  int offset = selectedEditor.getDocument().getLineStartOffset(pos.line) + pos.column;
                  PsiElement element = file.findElementAt(offset);
                  if (element != null) {
                    component.setData(element, hintText, true);
                    final JBPopup popup =
                      JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
                        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
                        .setResizable(true)
                        .setMovable(true)
                        .setRequestFocus(true)
                        .createPopup();
                    component.setHint(popup);
                    popup.showInFocusCenter();
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
