// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class CodeCleanupWriteActionTest extends BasePlatformTestCase {
  /**
   * The analysis behind code cleanup needs a background thread without a read lock. Starting it from inside a write action used to
   * run it inline on the calling thread instead, because headless tasks are run synchronously.
   */
  public void testCleanupStartedInsideWriteAction() {
    PsiFile file = myFixture.configureByText("a.txt", "foo");
    myFixture.enableInspections(new ReplaceFooInspection());
    AtomicBoolean finished = new AtomicBoolean();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      GlobalInspectionContextBase.cleanupElements(getProject(), () -> finished.set(true), file);
    });
    awaitCleanup(finished);
    assertEquals("bar", myFixture.getEditor().getDocument().getText());
  }

  public void testCleanupStartedOutsideWriteAction() {
    PsiFile file = myFixture.configureByText("a.txt", "foo");
    myFixture.enableInspections(new ReplaceFooInspection());
    AtomicBoolean finished = new AtomicBoolean();
    GlobalInspectionContextBase.cleanupElements(getProject(), () -> finished.set(true), file);
    awaitCleanup(finished);
    assertEquals("bar", myFixture.getEditor().getDocument().getText());
  }

  private static void awaitCleanup(@NotNull AtomicBoolean finished) {
    PlatformTestUtil.waitWithEventsDispatching("Code cleanup did not finish", finished::get, 30);
  }

  private static final class ReplaceFooInspection extends LocalInspectionTool implements CleanupLocalInspectionTool {
    @Override
    public boolean runForWholeFile() {
      return true;
    }

    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
      int offset = file.getText().indexOf("foo");
      if (offset < 0) return null;
      ProblemDescriptor descriptor = manager.createProblemDescriptor(file, new TextRange(offset, offset + "foo".length()),
                                                                    "Replace with bar", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                    isOnTheFly, new ReplaceFooFix());
      return new ProblemDescriptor[]{descriptor};
    }
  }

  private static final class ReplaceFooFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return "Replace foo with bar";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;
      PsiFile file = element.getContainingFile();
      Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (document == null) return;
      TextRange range = descriptor.getTextRangeInElement().shiftRight(element.getTextRange().getStartOffset());
      document.replaceString(range.getStartOffset(), range.getEndOffset(), "bar");
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }
}
