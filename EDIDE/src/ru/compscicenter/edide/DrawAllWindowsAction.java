package ru.compscicenter.edide;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: liana
 * data: 6/18/14.
 * Action for drawing all task windows in task file.
 * If there is some task window already drawn, action removes task windows.
 */
class DrawAllWindowsAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        TaskManager tm = TaskManager.getInstance();
        if (!(project != null && project.isOpen())) {
            return;
        }
        Editor editor = StudyEditor.getRecentOpenedEditor(project);
        if (editor == null) {
            return;
        }
        VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (vfOpenedFile == null) {
            return;
        }

        String fileName = vfOpenedFile.getName();
        int num = tm.getTaskNumForFile(fileName);
        TaskFile tf = tm.getTaskFile(num, fileName);
        int tw_num = tf.getTaskWindowNum();
        if (editor.getMarkupModel().getAllHighlighters().length >= tw_num) {
            editor.getMarkupModel().removeAllHighlighters();
            return;
        }
        tf.drawAllWindows(editor);
    }
}
