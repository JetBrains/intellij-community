package ru.compscicenter.edide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: liana
 * data: 6/18/14.
 * Action for marking task window as resolved and switching to next task window
 */
class ResolveAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = StudyEditor.getRecentOpenedEditor(e.getProject());
        if (editor == null) {
            return;
        }
        VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (vfOpenedFile != null) {
            String fileName = vfOpenedFile.getName();
            int currentTaskNum = TaskManager.getInstance(e.getProject()).getTaskNumForFile(fileName);
            TaskFile tf = TaskManager.getInstance(e.getProject()).getTaskFile(currentTaskNum, fileName);
            try {
                tf.resolveCurrentHighlighter(editor);
            }
            catch(IllegalArgumentException ex) {
                Log.print(ex.getMessage());
                Log.flush();
            }
        }
    }
}
