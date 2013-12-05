package ru.compscicenter.edide;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.util.FileContentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;


/**
 * User: lia
 */
public class StudyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull final EditorFactoryEvent event) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try {
                    VirtualFile vfOpenedFile = FileDocumentManager.getInstance().getFile(event.getEditor().getDocument());
                    if (vfOpenedFile != null) {
                        String fileName = vfOpenedFile.getNameWithoutExtension() + ".meta";
                        InputStream metaIS = StudyEditorFactoryListener.class.getResourceAsStream(fileName);
                        BufferedReader metaReader = new BufferedReader(new InputStreamReader(metaIS));
                        int replaceNum = Integer.parseInt(metaReader.readLine());

                        for (int i = 0; i < replaceNum; i++) {
                            int startOffset = Integer.parseInt(metaReader.readLine());
                            String textToWrite = metaReader.readLine();
                            String answer = metaReader.readLine();
                            PsiFile psiOpenFile = PsiManager.getInstance(event.getEditor().getProject()).findFile(vfOpenedFile);
                            Project project = event.getEditor().getProject();
                            event.getEditor().getDocument().createRangeMarker(startOffset, startOffset + textToWrite.length());
                            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(psiOpenFile);
                            int endOffset = startOffset + textToWrite.length();
                            TextRange range = new TextRange(startOffset, endOffset);
                            builder.replaceRange(range, textToWrite);
                            Template template = ((TemplateBuilderImpl) builder).buildInlineTemplate();
                            TemplateManager.getInstance(project).startTemplate(event.getEditor(), template);
                            event.getEditor().getCaretModel().moveToOffset(startOffset);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Something wrong with meta file");
                }
            }
        });

    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        System.out.println("Something else done!!!");
    }
}
