package ru.compscicenter.edide;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.util.TextRange;
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
                VirtualFile vf = event.getEditor().getProject().getBaseDir();
                VirtualFile[] children = vf.getChildren()[1].getChildren();
                VirtualFile task = children[0];
                PsiFile parentScope = PsiManager.getInstance(event.getEditor().getProject()).findFile(task);
                final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parentScope);
                final PsiElement[] childrenNodes = parentScope.getChildren();
                PsiElement replacedEl = childrenNodes[0].getChildren()[0];
                int textOffSet = replacedEl.getTextOffset();
                builder.replaceRange(TextRange.create(textOffSet, replacedEl.getTextOffset() + replacedEl.getTextLength()), replacedEl.toString());
                Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
                TemplateManager.getInstance(event.getEditor().getProject()).startTemplate(event.getEditor(), template);
                event.getEditor().getCaretModel().moveToOffset(textOffSet);
                builder.run(event.getEditor(), true);
            }
        });
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        System.out.println("Something else done!!!");
    }
}
