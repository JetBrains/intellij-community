package ru.compscicenter.edide;


import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import gherkin.deps.net.iharder.Base64;
import org.jetbrains.annotations.NotNull;

import java.io.*;


/**
 * User: lia
 */
public class StudyEditorFactoryListener implements EditorFactoryListener {

  protected boolean fileChanged(VirtualFile file) throws IOException {
    //TODO: surround with try-catch block
    File usual_file = new File(file.getPath());
    InputStream usual_file_stream = new FileInputStream(usual_file);
    InputStream metaIs = StudyEditorFactoryListener.class.getResourceAsStream(file.getName());
    BufferedReader bf_meta = new BufferedReader(new InputStreamReader(metaIs));
    BufferedReader bf_usual = new BufferedReader(new InputStreamReader(usual_file_stream));
    /*
    int sym;
    while( (sym = metaIs.read()) != -1) {
      int sym2 = usual_file_stream.read();
      if (sym != sym2) {
        return true;
      }
    }
    return false;
    */
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
    bf_usual.close();;
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
                  TaskManager taskManager = TaskManager.getInstance();
                  int currentTask = taskManager.getTaskNumForFile(vfOpenedFile.getName());
                  int finishedTask = taskManager.getCurrentTask();
                  if (currentTask < finishedTask) {
                    return;
                  }
                  int startOffset0 = 0;
                  final Project project = editor.getProject();
                  if (project == null) {
                    return;
                  }
                  PsiFile psiOpenFile = PsiManager.getInstance(project).findFile(vfOpenedFile);
                  final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(psiOpenFile);
                  for (int i = 0; i < taskManager.getTaskFile(currentTask, vfOpenedFile.getName()).getTaskWindowNum(); i++) {
                    TaskWindow tw = taskManager.getTaskFile(currentTask, vfOpenedFile.getName()).getTaskWindowByIndex(i);
                    int line = tw.getLine() - 1;
                    int lineOffset = editor.getDocument().getLineStartOffset(line);
                    int startOffset = lineOffset + tw.getStartOffset();
                    if (i == 0) {
                      startOffset0 = startOffset;
                    }
                    String textToWrite = tw.getText();
                    editor.getDocument().createRangeMarker(startOffset, startOffset + textToWrite.length());
                    int endOffset = startOffset + textToWrite.length();
                    TextRange range = new TextRange(startOffset, endOffset);
                    builder.replaceRange(range, textToWrite);
                  }
                  final int finalStartOffset = startOffset0;
                  CommandProcessor.getInstance().executeCommand(project, new Runnable() {
                    @Override
                    public void run() {
                      Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
                      TemplateManager.getInstance(project).startTemplate(editor, template);
                      editor.getCaretModel().moveToOffset(finalStartOffset);
                    }
                  }, null, null);
                }
              }
              catch (Exception e) {
                Log.print("Something wrong with meta file:" + e.getCause());
                Log.print(String.valueOf(e.getStackTrace()));
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
    System.out.println("Something else done!!!");
  }
}
