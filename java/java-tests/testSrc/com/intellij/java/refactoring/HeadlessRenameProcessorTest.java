// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameFailure;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class HeadlessRenameProcessorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testARefusedWriteIsReported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Boom.java", """
      public class Boom {
        int boom;
      }
      """);
    PsiField boom = findClass("Boom").findFieldByName("boom", false);
    assertNotNull(boom, "no field boom in the fixture");
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME,
                                     ContainerUtil.prepend(HeadlessRenamePsiElementProcessor.EP_NAME.getExtensionList(),
                                                           new RefusingRenameProcessor()),
                                     myFixture.getTestRootDisposable());

    HeadlessRenameResult result = performRename(boom, "count");

    ReadAction.runBlocking(() -> {
      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.WRITE_FAILED, failed.getKind());
      assertEquals("The test refuses the write.", failed.getDetail());
      assertNotNull(findClass("Boom").findFieldByName("boom", false), "the rename wrote to the file");
    });
  }

  /**
   * A throwable that is not an {@link IncorrectOperationException} reports the same kind.
   * <p>
   * The platform catches only that one type, so any other escapes {@code performRefactoring}. Without
   * the catch of the headless driver it would leave {@link com.intellij.refactoring.rename.RenamePlan}
   * and reach the caller raw.
   */
  @Test
  public void testAThrowableOfTheWriteIsReported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Crash.java", """
      public class Crash {
        int crash;
      }
      """);
    PsiField crash = findClass("Crash").findFieldByName("crash", false);
    assertNotNull(crash, "no field crash in the fixture");
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME,
                                     ContainerUtil.prepend(HeadlessRenamePsiElementProcessor.EP_NAME.getExtensionList(),
                                                           new BreakingRenameProcessor()),
                                     myFixture.getTestRootDisposable());

    HeadlessRenameResult result = performRename(crash, "count");

    ReadAction.runBlocking(() -> {
      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.WRITE_FAILED, failed.getKind());
      assertEquals("The test breaks the write.", failed.getDetail());
      assertNotNull(findClass("Crash").findFieldByName("crash", false), "the rename wrote to the file");
    });
  }

  @Test
  public void testAnElementOfTheDefaultProcessorIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Plain.java", """
      public class Plain {
        int hits;
        int read() {
          return hits;
        }
      }
      """);
    PsiField hits = findHitsField("Plain");
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME, List.of(), myFixture.getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(RenamePsiElementProcessorBase.EP_NAME, List.of(), myFixture.getTestRootDisposable());

    HeadlessRenameResult result = performRename(hits, "count");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      String text = findClass("Plain").getContainingFile().getText();
      assertTrue(text.contains("return count;"), "the usage kept the old name: " + text);
    });
  }

  /** A processor that can ask the user, and that carries no headless registration, refuses by name. */
  @Test
  public void testAProcessorWithNoHeadlessRegistrationIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Lonely.java", """
      public class Lonely {
        int hits;
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findHitsField("Lonely");
      ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME, List.of(), myFixture.getTestRootDisposable());

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, failed.getKind());
      assertEquals("RenameJavaVariableProcessor renames this element, and it has no headless registration.",
                   failed.getDetail());
      assertNotNull(findClass("Lonely").findFieldByName("hits", false), "the field lost its old name");
    });
  }

  /** A language with no {@code headlessRenameSupportedLanguage} statement refuses before the lookup. */
  @Test
  public void testAnUncoveredLanguageIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile notes = myFixture.addFileToProject("notes.txt", "a note\n");

    ReadAction.runBlocking(() -> {
      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), notes, "memo.txt");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, failed.getKind());
      assertEquals("notes.txt", notes.getName(), "notes.txt was renamed");
    });
  }

  /** Refuses the write of the field named boom, and answers every other question as Java does. */
  private static final class RefusingRenameProcessor extends RenameJavaVariableProcessor
    implements DelegatingHeadlessRenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
      return element instanceof PsiField field && "boom".equals(field.getName());
    }

    @Override
    public void renameElement(@NotNull PsiElement element,
                              @NotNull String newName,
                              UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) {
      throw new IncorrectOperationException("The test refuses the write");
    }
  }

  /** Breaks the write of the field named crash with a throwable the platform does not catch. */
  private static final class BreakingRenameProcessor extends RenameJavaVariableProcessor
    implements DelegatingHeadlessRenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
      return element instanceof PsiField field && "crash".equals(field.getName());
    }

    @Override
    public void renameElement(@NotNull PsiElement element,
                              @NotNull String newName,
                              UsageInfo @NotNull [] usages,
                              @Nullable RefactoringElementListener listener) {
      throw new IllegalStateException("The test breaks the write");
    }
  }

  private static HeadlessRenameResult performRename(PsiElement element, String newName) {
    return EdtTestUtil.runInEdtAndGet(() -> {
      HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(element.getProject(), element, newName);
      HeadlessRenameResult.Planned plan = assertInstanceOf(HeadlessRenameResult.Planned.class, planned);
      return plan.getPlan().apply();
    });
  }

  private PsiField findHitsField(String className) {
    PsiField field = ReadAction.computeBlocking(() -> findClass(className).findFieldByName("hits", false));
    assertNotNull(field, "no field hits in " + className);
    return field;
  }

  private PsiClass findClass(String name) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass psiClass = ReadAction.computeBlocking(() -> myFixture.findClass(name));
    assertNotNull(psiClass, "no class " + name + " in the fixture");
    return psiClass;
  }
}
