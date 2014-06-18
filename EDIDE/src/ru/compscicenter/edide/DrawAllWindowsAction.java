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
 */
public class DrawAllWindowsAction extends AnAction {
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
        int num = tm.getTaskNumForFile(vfOpenedFile.getName());
        TaskFile tf = tm.getTaskFile(num, vfOpenedFile.getName());
        int tw_num = tf.getTaskWindowNum();
        for (int i = 0; i < tw_num; i++) {
            tf.getTaskWindowByIndex(i).draw(editor);
        }
    }
}
