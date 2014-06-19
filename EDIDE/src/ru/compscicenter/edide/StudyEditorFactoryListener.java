package ru.compscicenter.edide;


import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;


import java.awt.*;
import java.io.*;
import java.util.Arrays;

import static com.intellij.openapi.editor.markup.EffectType.*;
import static com.intellij.ui.JBColor.*;


/**
 * User: lia
 */

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
        //myTaskFile.drawFirstUnresolved(editor, true);
   }

}
class StudyEditorFactoryListener implements EditorFactoryListener {

    boolean fileChanged(VirtualFile file) throws IOException {
    File usual_file = new File(file.getPath());
    InputStream usual_file_stream = new FileInputStream(usual_file);
    InputStream metaIs = StudyEditorFactoryListener.class.getResourceAsStream(file.getName());
    BufferedReader bf_meta = new BufferedReader(new InputStreamReader(metaIs));
    BufferedReader bf_usual = new BufferedReader(new InputStreamReader(usual_file_stream));
    while(bf_meta.ready()) {
      String line1 = bf_meta.readLine();
      String line2 = bf_usual.readLine();
      if (!line1.equals(line2)) {
        bf_meta.close();
        bf_usual.close();
        return true;
      }
    }
    bf_meta.close();
    bf_usual.close();
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
                    HintManager.getInstance().showInformationHint(editor, "Нажмите на любое окошко с заданием");
                    TaskManager taskManager = TaskManager.getInstance();
                   int currentTask = taskManager.getTaskNumForFile(vfOpenedFile.getName());
                   TaskFile tf = taskManager.getTaskFile(currentTask, vfOpenedFile.getName());
                    editor.addEditorMouseListener(new MyMouseListener(tf));
                    editor.getMarkupModel().removeAllHighlighters();
                    //tf.drawFirstUnresolved(editor, false);
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
