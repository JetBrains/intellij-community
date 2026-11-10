// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.DelegatingHeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameFailure;
import com.intellij.refactoring.rename.HeadlessRenameProcessor;
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor;
import com.intellij.refactoring.rename.HeadlessRenameResult;
import com.intellij.refactoring.rename.ReadOnlyUsagePolicy;
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

import java.io.IOException;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class HeadlessRenameProcessorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testFieldAndItsUsageAreRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Counter.java", """
      public class Counter {
        int hits;
      }
      """);
    myFixture.addFileToProject("Reader.java", """
      public class Reader {
        int read(Counter counter) {
          return counter.hits;
        }
      }
      """);
    PsiField hits = findHitsField("Counter");

    HeadlessRenameResult result = performRename(hits, "count");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertNotNull(findClass("Counter").findFieldByName("count", false), "the field kept the old name");
      String reader = findClass("Reader").getContainingFile().getText();
      assertTrue(reader.contains("counter.count"), "the usage kept the old name: " + reader);
    });
  }

  @Test
  public void testOverridingMethodIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Base.java", """
      public class Base {
        public void run() {}
      }
      """);
    myFixture.addFileToProject("Impl.java", """
      public class Impl extends Base {
        @Override
        public void run() {}
      }
      """);
    PsiMethod[] baseRun = ReadAction.computeBlocking(() -> findClass("Base").findMethodsByName("run", false));
    assertThat(baseRun).hasSize(1);

    HeadlessRenameResult result = performRename(baseRun[0], "execute");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertThat(findClass("Base").findMethodsByName("execute", false)).hasSize(1);
      assertThat(findClass("Impl").findMethodsByName("execute", false)).hasSize(1);
      assertThat(findClass("Impl").findMethodsByName("run", false)).hasSize(0);
    });
  }

  @Test
  public void testEveryOverrideOfADeepHierarchyIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Hierarchy.java", """
      class Parent {
        void test() {}
      }

      class Child extends Parent {
        @Override
        void test() {}
      }

      class Child2 extends Child {
        @Override
        void test() {}
      }
      """);
    PsiMethod[] deepest = ReadAction.computeBlocking(() -> findClass("Child2").findMethodsByName("test", false));
    assertThat(deepest).hasSize(1);

    EdtTestUtil.runInEdtAndWait(() -> {
      HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(myFixture.getProject(), deepest[0], "test2");
      HeadlessRenameResult.Planned plan = assertInstanceOf(HeadlessRenameResult.Planned.class, planned);
      PsiElement substituted = plan.getPlan().getPrimaryElement();
      assertEquals(findClass("Parent").findMethodsByName("test", false)[0],
                   substituted, "the target was not substituted with the base method");
      HeadlessRenameResult result = plan.getPlan().apply();

      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertThat(findClass("Parent").findMethodsByName("test2", false)).hasSize(1);
      assertThat(findClass("Child").findMethodsByName("test2", false)).hasSize(1);
      assertThat(findClass("Child2").findMethodsByName("test2", false)).hasSize(1);
    });
  }

  @Test
  public void testAnalyzeWritesNothing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("Plain.java", """
      public class Plain {
        int hits;
      }
      """);
    ReadAction.runBlocking(() -> {
      String before = file.getText();
      PsiField hits = findHitsField("Plain");

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count");

      HeadlessRenameResult.Planned planned = assertInstanceOf(HeadlessRenameResult.Planned.class, result);
      assertThat(planned.getPlan().getAffectedFiles()).contains(file.getVirtualFile());
      assertEquals(before, file.getText(), "analyze wrote to the file");
      assertNotNull(findClass("Plain").findFieldByName("hits", false), "the field lost its old name");
    });
  }

  @Test
  public void testConflictWithAnExistingFieldIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Holder.java", """
      public class Holder {
        int hits;
        int count;
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findHitsField("Holder");

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count");

      HeadlessRenameResult.Refused refused = assertInstanceOf(HeadlessRenameResult.Refused.class, result);
      assertFalse(refused.getConflicts().isEmpty(), "a refusal must name its conflict");
      assertNotNull(findClass("Holder").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

  @Test
  public void testUsageInAReadOnlyFileIsRefused() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Signal.java", """
      public class Signal {
        int hits;
      }
      """);
    PsiFile locked = myFixture.addFileToProject("Locked.java", """
      public class Locked {
        int read(Signal signal) {
          return signal.hits;
        }
      }
      """);
    VirtualFile lockedFile = locked.getVirtualFile();
    PsiField hits = findHitsField("Signal");

    HeadlessRenameResult result;
    WriteAction.run(() -> lockedFile.setWritable(false));
    try {
      result = ReadAction.computeBlocking(() -> HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count"));
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
    assertEquals(HeadlessRenameFailure.READ_ONLY_USAGES, failed.getKind());
    assertNotNull(failed.getDetail(), "the read-only file names itself in the report");
    assertNotNull(findClass("Signal").findFieldByName("hits", false), "the rename wrote to the file");
  }

  @Test
  public void testAReadOnlyDeclarationIsRefusedWithTheSkipPolicy() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile locked = myFixture.addFileToProject("Sealed.java", """
      public class Sealed {
        int hits;
      }
      """);
    VirtualFile lockedFile = locked.getVirtualFile();
    PsiField hits = findHitsField("Sealed");

    HeadlessRenameResult result;
    WriteAction.run(() -> lockedFile.setWritable(false));
    try {
      result = ReadAction.computeBlocking(() -> HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count", ReadOnlyUsagePolicy.SKIP, false));
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
    assertEquals(HeadlessRenameFailure.READ_ONLY_USAGES, failed.getKind());
    assertNotNull(failed.getDetail(), "the refusal must name the file");
    assertNotNull(findClass("Sealed").findFieldByName("hits", false), "the rename wrote to the file");
  }

  @Test
  public void testAReadOnlyUsageIsSkippedWithTheSkipPolicy() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Meter.java", """
      public class Meter {
        int hits;
      }
      """);
    PsiFile locked = myFixture.addFileToProject("Viewer.java", """
      public class Viewer {
        int read(Meter meter) {
          return meter.hits;
        }
      }
      """);
    VirtualFile lockedFile = locked.getVirtualFile();
    PsiField hits = findHitsField("Meter");

    HeadlessRenameResult result;
    WriteAction.run(() -> lockedFile.setWritable(false));
    try {
      result = ReadAction.computeBlocking(() -> HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count", ReadOnlyUsagePolicy.SKIP, false));
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Planned planned = assertInstanceOf(HeadlessRenameResult.Planned.class, result);
    assertThat(planned.getPlan().getNotes()).hasSize(1);
    assertTrue(planned.getPlan().getNotes().getFirst().contains(lockedFile.getPath()),
               "the note must name the file: " + planned.getPlan().getNotes());
  }

  @Test
  public void testAnAutomaticRenamerRenamesAVariableOfTheClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Widget.java", """
      public class Widget {
      }
      """);
    myFixture.addFileToProject("Holder.java", """
      public class Holder {
        void use() {
          Widget widget = null;
        }
      }
      """);
    PsiClass widget = findClass("Widget");

    EdtTestUtil.runInEdtAndWait(() -> {
      HeadlessRenameResult analysis =
        HeadlessRenameProcessor.analyze(myFixture.getProject(), widget, "Gadget", ReadOnlyUsagePolicy.REFUSE, true);
      HeadlessRenameResult result = assertInstanceOf(HeadlessRenameResult.Planned.class, analysis).getPlan().apply();

      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      String holder = findClass("Holder").getContainingFile().getText();
      assertTrue(holder.contains("Gadget gadget"), "the variable kept the old name: " + holder);
    });
  }

  @Test
  public void testACollisionOfAnAutomaticRenameIsReported() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Gear.java", """
      public class Gear {
      }
      """);
    myFixture.addFileToProject("Box.java", """
      public class Box {
        void use() {
          Gear gear = null;
          int cog = 0;
        }
      }
      """);
    PsiClass gear = findClass("Gear");

    // The renamer wants the variable gear to become cog, and the method already holds a variable cog.
    EdtTestUtil.runInEdtAndWait(() -> {
      HeadlessRenameResult analysis =
        HeadlessRenameProcessor.analyze(myFixture.getProject(), gear, "Cog", ReadOnlyUsagePolicy.REFUSE, true);

      HeadlessRenameResult.Planned planned = assertInstanceOf(HeadlessRenameResult.Planned.class, analysis);
      assertThat(planned.getPlan().getNotes()).hasSize(1);
      assertTrue(planned.getPlan().getNotes().getFirst().contains("cog"),
                 "the note must name the collision: " + planned.getPlan().getNotes());
      assertInstanceOf(HeadlessRenameResult.Applied.class, planned.getPlan().apply());
      String box = findClass("Box").getContainingFile().getText();
      assertTrue(box.contains("Cog gear"), "the variable took a name that is taken: " + box);
    });
  }

  @Test
  public void testMethodWithALibraryBaseMethodIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Task.java", """
      public class Task implements Runnable {
        @Override
        public void run() {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiMethod[] taskRun = findClass("Task").findMethodsByName("run", false);
      assertThat(taskRun).hasSize(1);

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), taskRun[0], "execute");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, failed.getKind());
      assertThat(findClass("Task").findMethodsByName("run", false)).hasSize(1);
    });
  }

  @Test
  public void testAnInvalidNewNameIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Gauge.java", """
      public class Gauge {
        int hits;
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiField hits = findHitsField("Gauge");

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "not a name");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.NEW_NAME_REFUSED, failed.getKind());
      assertNotNull(failed.getDetail(), "a refusal must name its reason");
      assertNotNull(findClass("Gauge").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

  @Test
  public void testAnInvalidPackageNameIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack3/Meter.java", """
      package pack3;

      public class Meter {
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiPackage pack3 = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("pack3");
      assertNotNull(pack3, "no package pack3 in the fixture");

      // PsiPackageRenameValidator states this refusal as a message, and RenameUtil.isValidName lets it pass.
      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), pack3, "1bad");

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.NEW_NAME_REFUSED, failed.getKind());
      assertNotNull(failed.getDetail(), "a refusal must name its reason");
      assertNotNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("pack3"), "the package lost its old name");
    });
  }

  @Test
  public void testAQualifiedPackageNameWithTheSameParentIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("outer/inner/Gear.java", """
      package outer.inner;

      public class Gear {
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiPackage inner = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("outer.inner");
      assertNotNull(inner, "no package outer.inner in the fixture");

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), inner, "outer.renamed");

      //just refuse until moving is implemented
      assertInstanceOf(HeadlessRenameResult.Refused.class, result);
    });
  }

  /** A qualified name that changes the parent package asks for a move, and a rename holds no move. */
  @Test
  public void testAQualifiedPackageNameThatMovesIsRefused() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack8/Axle.java", """
      package pack8;

      public class Axle {
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiPackage pack8 = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("pack8");
      assertNotNull(pack8, "no package pack8 in the fixture");

      HeadlessRenameResult result = HeadlessRenameProcessor.analyze(myFixture.getProject(), pack8, "other.pack9");

      HeadlessRenameResult.Refused refused = assertInstanceOf(HeadlessRenameResult.Refused.class, result);
      assertFalse(refused.getConflicts().isEmpty(), "a refusal must name its conflict");
      assertNotNull(JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("pack8"), "the rename wrote to the project");
    });
  }

  /**
   * A directory of a package is renamed as the package. DirectoryAsPackageRenameHandler answers the
   * same way when one directory holds the package, and it asks the user nothing there.
   */
  @Test
  public void testAPackageDirectoryIsRenamedAsAPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile wheel = myFixture.addFileToProject("pack6/Wheel.java", """
      package pack6;

      public class Wheel {
      }
      """);
    PsiDirectory directory = ReadAction.computeBlocking(() -> wheel.getContainingDirectory());
    assertNotNull(directory, "no directory for the file");

    HeadlessRenameResult result = performRename(directory, "pack7");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      PsiFile moved = findClass("pack7.Wheel").getContainingFile();
      assertEquals("pack7", ((PsiJavaFile)moved).getPackageName(), "the package statement kept the old name");
      PsiDirectory movedDirectory = moved.getContainingDirectory();
      assertNotNull(movedDirectory, "the file left the tree");
      assertEquals("pack7", movedDirectory.getName(), "the directory kept the old name");
    });
  }

  @Test
  public void testPackageDirectoryIsRenamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pack1/Widget.java", """
      package pack1;

      public class Widget {
      }
      """);
    PsiPackage pack1 = JavaPsiFacade.getInstance(myFixture.getProject()).findPackage("pack1");
    assertNotNull(pack1, "no package pack1 in the fixture");

    HeadlessRenameResult result = performRename(pack1, "pack2");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      PsiFile widget = findClass("pack2.Widget").getContainingFile();
      assertEquals("pack2", ((PsiJavaFile)widget).getPackageName(), "the package statement kept the old name");
      PsiDirectory directory = widget.getContainingDirectory();
      assertNotNull(directory, "the file left the tree");
      assertEquals("pack2", directory.getName(), "the file stayed in the old directory");
    });
  }

  /**
   * A rename of the case only renames the file too. The file system of the fixture ignores the case
   * on Windows and on macOS, and there the check for a taken name reads the file itself. On Linux the
   * file system keeps the case apart, so the test is a plain rename there.
   */
  @Test
  public void testARenameOfTheCaseRenamesTheFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("gauge.java", """
      public class gauge {
      }
      """);
    PsiClass gauge = findClass("gauge");

    HeadlessRenameResult result = performRename(gauge, "Gauge");

    ReadAction.runBlocking(() -> {
      HeadlessRenameResult.Applied applied = assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertThat(applied.getSkippedFiles()).as("the rename of the file was skipped").isNullOrEmpty();
      assertEquals("Gauge.java", findClass("Gauge").getContainingFile().getName(), "the file kept the old name");
    });
  }

  @Test
  public void testPlanRunsOnlyOnce() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("Once.java", """
      public class Once {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Once");
    EdtTestUtil.runInEdtAndWait(() -> {
      HeadlessRenameResult.Planned planned =
        assertInstanceOf(HeadlessRenameResult.Planned.class, HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count"));
      assertInstanceOf(HeadlessRenameResult.Applied.class, planned.getPlan().apply());

      HeadlessRenameResult second = planned.getPlan().apply();

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, second);
      assertEquals(HeadlessRenameFailure.PLAN_STALE, failed.getKind());
      assertNotNull(failed.getDetail(), "a plan that already ran says so");
    });
  }

  @Test
  public void testAnUncommittedChangeStopsThePlan() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("Pulse.java", """
      public class Pulse {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Pulse");
    EdtTestUtil.runInEdtAndWait(() -> {
      HeadlessRenameResult.Planned planned =
        assertInstanceOf(HeadlessRenameResult.Planned.class, HeadlessRenameProcessor.analyze(myFixture.getProject(), hits, "count"));
      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      assertNotNull(document, "no document for the file");

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> document.insertString(0, "// a note\n"));
      assertTrue(PsiDocumentManager.getInstance(myFixture.getProject()).isUncommited(document),
                 "the fixture committed the change, so this test proves nothing");

      HeadlessRenameResult result = planned.getPlan().apply();

      HeadlessRenameResult.Failed failed = assertInstanceOf(HeadlessRenameResult.Failed.class, result);
      assertEquals(HeadlessRenameFailure.PLAN_STALE, failed.getKind());
      assertNotNull(findClass("Pulse").findFieldByName("hits", false), "the rename wrote to the file");
    });
  }

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

  /**
   * A directory passes the language gate, because its language is {@link Language#ANY}.
   * <p>
   * {@code PsiDirectoryImpl.getLanguage} answers with that one, so a directory names no language the
   * gate could read, and the processor list decides instead.
   */
  @Test
  public void testADirectoryPassesTheLanguageGate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile notes = myFixture.addFileToProject("data/notes.txt", "a note\n");
    PsiDirectory data = ReadAction.computeBlocking(() -> notes.getContainingDirectory());
    assertNotNull(data, "no directory data in the fixture");
    assertEquals(Language.ANY, data.getLanguage(), "PsiDirectory no longer answers with Language.ANY");

    HeadlessRenameResult result = performRename(data, "info");

    ReadAction.runBlocking(() -> {
      assertInstanceOf(HeadlessRenameResult.Applied.class, result);
      assertEquals("info", data.getName(), "the directory kept the old name");
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
