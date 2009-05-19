/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
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
  private final String myNewFileName;

  /**
   * @param newFileName with extension
   */
  public RenameFileFix(String newFileName) {
    myNewFileName = newFileName;
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("rename.file.fix");
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("rename.file.fix");
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
    if (vFile == null) return false;
    final VirtualFile parent = vFile.getParent();
    if (parent == null) return false;
    final VirtualFile newVFile = parent.findChild(myNewFileName);
    return newVFile == null || newVFile.equals(vFile);
  }


  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    FileDocumentManager.getInstance().saveDocument(document);
    try {
      vFile.rename(file.getManager(), myNewFileName);
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