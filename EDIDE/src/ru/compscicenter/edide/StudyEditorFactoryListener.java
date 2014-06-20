package ru.compscicenter.edide;


import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;


/**
 * User: lia
 */


class StudyEditorFactoryListener implements EditorFactoryListener {

    class MyMouseListener extends EditorMouseAdapter {
        private final TaskFile myTaskFile;

        MyMouseListener(TaskFile taskFile) {
            myTaskFile = taskFile;
        }

        @Override
        public void mouseClicked(EditorMouseEvent e){
            Editor editor = e.getEditor();
            LogicalPosition pos = editor.xyToLogicalPosition(e.getMouseEvent().getPoint());
            editor.getMarkupModel().removeAllHighlighters();
            myTaskFile.drawWindowByPos(editor, pos);
        }

    }

    boolean fileChanged(VirtualFile file) throws IOException {
    File usualFile = new File(file.getPath());
    InputStream usualFileStream = new FileInputStream(usualFile);
    InputStream metaIs = StudyEditorFactoryListener.class.getResourceAsStream(file.getName());
    BufferedReader bfMeta = new BufferedReader(new InputStreamReader(metaIs));
    BufferedReader bfUsual = new BufferedReader(new InputStreamReader(usualFileStream));
    while(bfMeta.ready()) {
      String line1 = bfMeta.readLine();
      String line2 = bfUsual.readLine();
      if (!line1.equals(line2)) {
        bfMeta.close();
        bfUsual.close();
        return true;
      }
    }
    bfMeta.close();
    bfUsual.close();
    return false;
  }

  @Override
  public void editorCreated(@NotNull final EditorFactoryEvent event) {
      ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                final Editor editor = event.getEditor();

                VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
                if (vfOpenedFile != null) {
                  if (fileChanged(vfOpenedFile)) {
                    return;
                  }
                    HintManager.getInstance().showInformationHint(editor, "Select any task window");
                    TaskManager taskManager = TaskManager.getInstance();
                   int currentTask = taskManager.getTaskNumForFile(vfOpenedFile.getName());
                   TaskFile tf = taskManager.getTaskFile(currentTask, vfOpenedFile.getName());
                    editor.addEditorMouseListener(new MyMouseListener(tf));
                    editor.getMarkupModel().removeAllHighlighters();
                    tf.drawAllWindows(editor);
                }
              }
              catch (Exception e) {
                Log.print("Something wrong with meta file:" + e.getCause());
                Log.print(Arrays.toString(e.getStackTrace()));
                Log.print(e.getMessage());
                Log.flush();
              }
            }
          });
        }
      }
    );
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
      Log.print("Editor released\n");
      Log.flush();
  }
}
