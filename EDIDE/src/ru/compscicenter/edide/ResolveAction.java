package ru.compscicenter.edide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: liana
 * data: 6/18/14.
 */
public class ResolveAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = StudyEditor.getRecentOpenedEditor(e.getProject());
        if (editor == null) {
            return;
        }
        LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
        VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (vfOpenedFile != null) {
            int currentTaskNum = TaskManager.getInstance().getTaskNumForFile(vfOpenedFile.getName());
            TaskFile tf = TaskManager.getInstance().getTaskFile(currentTaskNum, vfOpenedFile.getName());
            try {
                tf.resolveCurrentHighlighter(editor, pos);
            }
            catch(IllegalArgumentException ex) {
                return;
            }
            tf.drawFirstUnresolved(editor);
        }
    }
}
