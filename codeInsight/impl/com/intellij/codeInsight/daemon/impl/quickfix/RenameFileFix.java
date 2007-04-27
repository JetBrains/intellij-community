/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class RenameFileFix implements IntentionAction, LocalQuickFix {
  private final String myNewName;

  public RenameFileFix(String newName) {
    myNewName = newName;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("rename.file.fix");
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("rename.file.fix");
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiFile file = descriptor.getPsiElement().getContainingFile();
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        protected void run(Result result) throws Throwable {
          invoke(project, FileEditorManager.getInstance(project).getSelectedTextEditor(), file);
        }
      }.execute();
    }
  }

  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!file.isValid()) return false;
    VirtualFile vFile = file.getVirtualFile();
    String newName = myNewName + "." + vFile.getExtension();

    final VirtualFile parent = vFile.getParent();
    assert parent != null;
    final VirtualFile newVFile = parent.findChild(newName);
    return newVFile == null || newVFile.equals(vFile);
  }


  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    String newName = myNewName + "." + vFile.getExtension();
    FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(file));
    try{
      vFile.rename(file.getManager(), newName);
    }
    catch(IOException e){
      MessagesEx.error(project, e.getMessage()).showLater();
    }
    if (editor != null) {
      DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}