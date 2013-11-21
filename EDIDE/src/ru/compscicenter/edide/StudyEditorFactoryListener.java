package ru.compscicenter.edide;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;


/**
 * User: lia
 */
public class StudyEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull final EditorFactoryEvent event) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                VirtualFile openedFile = FileDocumentManager.getInstance().getFile(event.getEditor().getDocument());
                if (openedFile != null) {
                    PsiFile parentScope = PsiManager.getInstance(event.getEditor().getProject()).findFile(openedFile);
                    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
                    final PsiElement[] childrenNodes = parentScope.getChildren();
                    PsiElement replacedEl = childrenNodes[0].getChildren()[0];
                    int offset = replacedEl.getTextOffset();
                    builder.replaceElement(replacedEl, replacedEl.getText());
                    Template template = ((TemplateBuilderImpl) builder).buildInlineTemplate();
                    TemplateManager.getInstance(event.getEditor().getProject()).startTemplate(event.getEditor(), template);
                    System.out.println(replacedEl.getTextOffset());
                    event.getEditor().getCaretModel().moveToOffset(offset);

                }
            }
        });
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        System.out.println("Something else done!!!");
    }
}
