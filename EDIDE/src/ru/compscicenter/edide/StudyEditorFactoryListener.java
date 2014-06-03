package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
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
import org.jetbrains.annotations.NotNull;

import java.io.*;


/**
 * User: lia
 */
public class StudyEditorFactoryListener implements EditorFactoryListener {

    protected boolean fileChanged (VirtualFile file) throws IOException {
        File usual_file = new File(file.getPath());
        File template = new File(StudyEditorFactoryListener.class.getResource(file.getName()).getFile());
        char[] text1 = FileUtil.loadFileText(usual_file);
        char[] text2 = FileUtil.loadFileText(template);
        for (int i = 0; i < text1.length; i++) {
            boolean r = (text1[i] == text2[i]);
            if (r == false) return true;
        }
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
                                                Template template = ((TemplateBuilderImpl) builder).buildInlineTemplate();
                                                TemplateManager.getInstance(project).startTemplate(editor, template);
                                                editor.getCaretModel().moveToOffset(finalStartOffset);
                                            }
                                        }, null, null);

                                    }
                                } catch (Exception e) {
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
