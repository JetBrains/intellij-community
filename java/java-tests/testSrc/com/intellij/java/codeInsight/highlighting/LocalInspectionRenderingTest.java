package com.intellij.java.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LocalInspectionRenderingTest extends DaemonAnalyzerTestCase {
  public void testStatusBarMustShowWhitespacesInsteadOfNbspForHtmlDescription() {
    enableInspectionTool(new LocalInspectionTool() {
      @NotNull
      @Override
      public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PsiElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            if (element.getParent() == null || element.getTextRange() == null || !element.getTextRange().equals(element.getParent().getTextRange())) { // to avoid useless duplicates
              holder.registerProblem(element, "<html>Do&nbsp;ha");
            }
          }
        };
      }
    });

    String text = "xxx";
    configureByText(PlainTextFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> myEditor.getDocument().setText(text));
    getEditor().getCaretModel().moveToOffset(getFile().getText().indexOf("if (") + 1);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    for (HighlightInfo info : infos) {
      assertEquals("Do ha", info.getDescription());
    }
  }
}
