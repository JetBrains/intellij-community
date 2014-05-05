package ru.compscicenter.edide;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
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
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try {
                    Editor editor = event.getEditor();
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
                        String fileName = vfOpenedFile.getNameWithoutExtension() + ".json";
                        InputStream metaIS = StudyEditorFactoryListener.class.getResourceAsStream(fileName);
                        if (metaIS == null) {
                            return;
                        }
                        BufferedReader metaReader = new BufferedReader(new InputStreamReader(metaIS));
                        JsonReader reader = new JsonReader(metaReader);
                        JsonParser parser = new JsonParser();
                        com.google.gson.JsonObject obj = parser.parse(reader).getAsJsonObject();
                        int replaceNum = obj.get("windows_num").getAsInt();
                        int startOffset0 = 0;
                        Project project = editor.getProject();
                        PsiFile psiOpenFile = PsiManager.getInstance(project).findFile(vfOpenedFile);
                        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(psiOpenFile);
                        JsonArray replaceList = obj.get("windows_description").getAsJsonArray();
                        int i = 0;
                        for (com.google.gson.JsonElement replacement : replaceList) {
                            int line = replacement.getAsJsonObject().get("line").getAsInt() - 1;
                            int lineOffset = editor.getDocument().getLineStartOffset(line);
                            int startOffset = lineOffset + replacement.getAsJsonObject().get("start").getAsInt();
                            if (i == 0) {
                                startOffset0 = startOffset;
                            }
                            String textToWrite = replacement.getAsJsonObject().get("text").getAsString();
                            String answer = replacement.getAsJsonObject().get("possible answer").getAsString();
                            editor.getDocument().createRangeMarker(startOffset, startOffset + textToWrite.length());
                            int endOffset = startOffset + textToWrite.length();
                            TextRange range = new TextRange(startOffset, endOffset);
                            builder.replaceRange(range, textToWrite);
                            i++;
                        }
                        Template template = ((TemplateBuilderImpl) builder).buildInlineTemplate();
                        TemplateManager.getInstance(project).startTemplate(editor, template);
                        editor.getCaretModel().moveToOffset(startOffset0);
                    }
                } catch (Exception e) {
                    Log.print("Something wrong with meta file:" + e.getCause());
                    Log.flush();
                }
            }
        });

    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        System.out.println("Something else done!!!");
    }
}
