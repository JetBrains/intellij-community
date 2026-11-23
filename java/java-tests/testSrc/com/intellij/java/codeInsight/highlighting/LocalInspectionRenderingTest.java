package com.intellij.java.codeInsight.highlighting;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class LocalInspectionRenderingTest {
  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testStatusBarMustShowWhitespacesInsteadOfNbspForHtmlDescription() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new LocalInspectionTool() {
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
    fixture.configureByText(PlainTextFileType.INSTANCE, text);
    WriteCommandAction.runWriteCommandAction(fixture.getProject(), () -> fixture.getEditor().getDocument().setText(text));
    int offset = ReadAction.computeBlocking(() -> fixture.getFile().getText().indexOf("if (") + 1);
    EdtTestUtil.runInEdtAndWait(() -> fixture.getEditor().getCaretModel().moveToOffset(offset));
    List<HighlightInfo> infos = fixture.doHighlighting(HighlightSeverity.WARNING);
    for (HighlightInfo info : infos) {
      assertEquals("Do ha", info.getDescription());
    }
  }
}
