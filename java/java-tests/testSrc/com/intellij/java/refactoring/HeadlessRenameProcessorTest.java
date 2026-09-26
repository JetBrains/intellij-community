// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.lang.Language;
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
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class HeadlessRenameProcessorTest extends LightJavaCodeInsightFixtureTestCase {

  public void testFieldAndItsUsageAreRenamed() {
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

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertNotNull("the field kept the old name", findClass("Counter").findFieldByName("count", false));
    String reader = findClass("Reader").getContainingFile().getText();
    assertTrue("the usage kept the old name: " + reader, reader.contains("counter.count"));
  }

  public void testOverridingMethodIsRenamed() {
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
    PsiMethod[] baseRun = findClass("Base").findMethodsByName("run", false);
    assertSize(1, baseRun);

    HeadlessRenameResult result = performRename(baseRun[0], "execute");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertSize(1, findClass("Base").findMethodsByName("execute", false));
    assertSize(1, findClass("Impl").findMethodsByName("execute", false));
    assertSize(0, findClass("Impl").findMethodsByName("run", false));
  }

  public void testEveryOverrideOfADeepHierarchyIsRenamed() {
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
    PsiMethod[] deepest = findClass("Child2").findMethodsByName("test", false);
    assertSize(1, deepest);

    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(getProject(), deepest[0], "test2");
    HeadlessRenameResult.Planned plan = assertInstanceOf(planned, HeadlessRenameResult.Planned.class);
    PsiElement substituted = plan.getPlan().getPrimaryElement();
    assertEquals("the target was not substituted with the base method",
                 findClass("Parent").findMethodsByName("test", false)[0], substituted);
    HeadlessRenameResult result = plan.getPlan().apply();

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertSize(1, findClass("Parent").findMethodsByName("test2", false));
    assertSize(1, findClass("Child").findMethodsByName("test2", false));
    assertSize(1, findClass("Child2").findMethodsByName("test2", false));
  }

  public void testAnalyzeWritesNothing() {
    PsiFile file = myFixture.addFileToProject("Plain.java", """
      public class Plain {
        int hits;
      }
      """);
    String before = file.getText();
    PsiField hits = findHitsField("Plain");

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), hits, "count");

    HeadlessRenameResult.Planned planned = assertInstanceOf(result, HeadlessRenameResult.Planned.class);
    assertContainsElements(planned.getPlan().getAffectedFiles(), file.getVirtualFile());
    assertEquals("analyze wrote to the file", before, file.getText());
    assertNotNull("the field lost its old name", findClass("Plain").findFieldByName("hits", false));
  }

  public void testConflictWithAnExistingFieldIsRefused() {
    myFixture.addFileToProject("Holder.java", """
      public class Holder {
        int hits;
        int count;
      }
      """);
    PsiField hits = findHitsField("Holder");

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), hits, "count");

    HeadlessRenameResult.Refused refused = assertInstanceOf(result, HeadlessRenameResult.Refused.class);
    assertFalse("a refusal must name its conflict", refused.getConflicts().isEmpty());
    assertNotNull("the rename wrote to the file", findClass("Holder").findFieldByName("hits", false));
  }

  public void testUsageInAReadOnlyFileIsRefused() throws IOException {
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
      result = HeadlessRenameProcessor.analyze(getProject(), hits, "count");
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.READ_ONLY_USAGES, failed.getKind());
    assertNotNull("the read-only file names itself in the report", failed.getDetail());
    assertNotNull("the rename wrote to the file", findClass("Signal").findFieldByName("hits", false));
  }

  public void testAReadOnlyDeclarationIsRefusedWithTheSkipPolicy() throws IOException {
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
      result = HeadlessRenameProcessor.analyze(getProject(), hits, "count", ReadOnlyUsagePolicy.SKIP, false);
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.READ_ONLY_USAGES, failed.getKind());
    assertNotNull("the refusal must name the file", failed.getDetail());
    assertNotNull("the rename wrote to the file", findClass("Sealed").findFieldByName("hits", false));
  }

  public void testAReadOnlyUsageIsSkippedWithTheSkipPolicy() throws IOException {
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
      result = HeadlessRenameProcessor.analyze(getProject(), hits, "count", ReadOnlyUsagePolicy.SKIP, false);
    }
    finally {
      WriteAction.run(() -> lockedFile.setWritable(true));
    }

    HeadlessRenameResult.Planned planned = assertInstanceOf(result, HeadlessRenameResult.Planned.class);
    assertSize(1, planned.getPlan().getNotes());
    assertTrue("the note must name the file: " + planned.getPlan().getNotes(),
               planned.getPlan().getNotes().getFirst().contains(lockedFile.getPath()));
  }

  public void testAnAutomaticRenamerRenamesAVariableOfTheClass() {
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

    HeadlessRenameResult analysis =
      HeadlessRenameProcessor.analyze(getProject(), widget, "Gadget", ReadOnlyUsagePolicy.REFUSE, true);
    HeadlessRenameResult result = assertInstanceOf(analysis, HeadlessRenameResult.Planned.class).getPlan().apply();

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    String holder = findClass("Holder").getContainingFile().getText();
    assertTrue("the variable kept the old name: " + holder, holder.contains("Gadget gadget"));
  }

  public void testACollisionOfAnAutomaticRenameIsReported() {
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
    HeadlessRenameResult analysis =
      HeadlessRenameProcessor.analyze(getProject(), gear, "Cog", ReadOnlyUsagePolicy.REFUSE, true);

    HeadlessRenameResult.Planned planned = assertInstanceOf(analysis, HeadlessRenameResult.Planned.class);
    assertSize(1, planned.getPlan().getNotes());
    assertTrue("the note must name the collision: " + planned.getPlan().getNotes(),
               planned.getPlan().getNotes().getFirst().contains("cog"));
    assertInstanceOf(planned.getPlan().apply(), HeadlessRenameResult.Applied.class);
    String box = findClass("Box").getContainingFile().getText();
    assertTrue("the variable took a name that is taken: " + box, box.contains("Cog gear"));
  }

  public void testMethodWithALibraryBaseMethodIsRefused() {
    myFixture.addFileToProject("Task.java", """
      public class Task implements Runnable {
        @Override
        public void run() {}
      }
      """);
    PsiMethod[] taskRun = findClass("Task").findMethodsByName("run", false);
    assertSize(1, taskRun);

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), taskRun[0], "execute");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.TARGET_NOT_RENAMABLE, failed.getKind());
    assertSize(1, findClass("Task").findMethodsByName("run", false));
  }

  public void testAnInvalidNewNameIsRefused() {
    myFixture.addFileToProject("Gauge.java", """
      public class Gauge {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Gauge");

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), hits, "not a name");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.NEW_NAME_REFUSED, failed.getKind());
    assertNotNull("a refusal must name its reason", failed.getDetail());
    assertNotNull("the rename wrote to the file", findClass("Gauge").findFieldByName("hits", false));
  }

  public void testAnInvalidPackageNameIsRefused() {
    myFixture.addFileToProject("pack3/Meter.java", """
      package pack3;

      public class Meter {
      }
      """);
    PsiPackage pack3 = JavaPsiFacade.getInstance(getProject()).findPackage("pack3");
    assertNotNull("no package pack3 in the fixture", pack3);

    // PsiPackageRenameValidator states this refusal as a message, and RenameUtil.isValidName lets it pass.
    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), pack3, "1bad");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.NEW_NAME_REFUSED, failed.getKind());
    assertNotNull("a refusal must name its reason", failed.getDetail());
    assertNotNull("the package lost its old name", JavaPsiFacade.getInstance(getProject()).findPackage("pack3"));
  }

  /** A qualified name keeps the parent package of the old name, so the rename takes its last part. */
  public void testAQualifiedPackageNameWithTheSameParentIsRenamed() {
    myFixture.addFileToProject("outer/inner/Gear.java", """
      package outer.inner;

      public class Gear {
      }
      """);
    PsiPackage inner = JavaPsiFacade.getInstance(getProject()).findPackage("outer.inner");
    assertNotNull("no package outer.inner in the fixture", inner);

    HeadlessRenameResult result = performRename(inner, "outer.renamed");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    PsiFile gear = findClass("outer.renamed.Gear").getContainingFile();
    assertEquals("the package statement kept the old name", "outer.renamed", ((PsiJavaFile)gear).getPackageName());
    PsiDirectory directory = gear.getContainingDirectory();
    assertNotNull("the file left the tree", directory);
    assertEquals("the directory kept the old name", "renamed", directory.getName());
  }

  /** A qualified name that changes the parent package asks for a move, and a rename holds no move. */
  public void testAQualifiedPackageNameThatMovesIsRefused() {
    myFixture.addFileToProject("pack8/Axle.java", """
      package pack8;

      public class Axle {
      }
      """);
    PsiPackage pack8 = JavaPsiFacade.getInstance(getProject()).findPackage("pack8");
    assertNotNull("no package pack8 in the fixture", pack8);

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), pack8, "other.pack9");

    HeadlessRenameResult.Refused refused = assertInstanceOf(result, HeadlessRenameResult.Refused.class);
    assertFalse("a refusal must name its conflict", refused.getConflicts().isEmpty());
    assertNotNull("the rename wrote to the project", JavaPsiFacade.getInstance(getProject()).findPackage("pack8"));
  }

  /**
   * A directory of a package is renamed as the package. DirectoryAsPackageRenameHandler answers the
   * same way when one directory holds the package, and it asks the user nothing there.
   */
  public void testAPackageDirectoryIsRenamedAsAPackage() {
    PsiFile wheel = myFixture.addFileToProject("pack6/Wheel.java", """
      package pack6;

      public class Wheel {
      }
      """);
    PsiDirectory directory = wheel.getContainingDirectory();
    assertNotNull("no directory for the file", directory);

    HeadlessRenameResult result = performRename(directory, "pack7");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    PsiFile moved = findClass("pack7.Wheel").getContainingFile();
    assertEquals("the package statement kept the old name", "pack7", ((PsiJavaFile)moved).getPackageName());
    PsiDirectory movedDirectory = moved.getContainingDirectory();
    assertNotNull("the file left the tree", movedDirectory);
    assertEquals("the directory kept the old name", "pack7", movedDirectory.getName());
  }

  public void testPackageDirectoryIsRenamed() {
    myFixture.addFileToProject("pack1/Widget.java", """
      package pack1;

      public class Widget {
      }
      """);
    PsiPackage pack1 = JavaPsiFacade.getInstance(getProject()).findPackage("pack1");
    assertNotNull("no package pack1 in the fixture", pack1);

    HeadlessRenameResult result = performRename(pack1, "pack2");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    PsiFile widget = findClass("pack2.Widget").getContainingFile();
    assertEquals("the package statement kept the old name", "pack2", ((PsiJavaFile)widget).getPackageName());
    PsiDirectory directory = widget.getContainingDirectory();
    assertNotNull("the file left the tree", directory);
    assertEquals("the file stayed in the old directory", "pack2", directory.getName());
  }

  /**
   * A rename of the case only renames the file too. The file system of the fixture ignores the case
   * on Windows and on macOS, and there the check for a taken name reads the file itself. On Linux the
   * file system keeps the case apart, so the test is a plain rename there.
   */
  public void testARenameOfTheCaseRenamesTheFile() {
    myFixture.addFileToProject("gauge.java", """
      public class gauge {
      }
      """);
    PsiClass gauge = findClass("gauge");

    HeadlessRenameResult result = performRename(gauge, "Gauge");

    HeadlessRenameResult.Applied applied = assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertEmpty("the rename of the file was skipped", applied.getSkippedFiles());
    assertEquals("the file kept the old name", "Gauge.java", findClass("Gauge").getContainingFile().getName());
  }

  public void testPlanRunsOnlyOnce() {
    myFixture.addFileToProject("Once.java", """
      public class Once {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Once");
    HeadlessRenameResult.Planned planned =
      assertInstanceOf(HeadlessRenameProcessor.analyze(getProject(), hits, "count"), HeadlessRenameResult.Planned.class);
    assertInstanceOf(planned.getPlan().apply(), HeadlessRenameResult.Applied.class);

    HeadlessRenameResult second = planned.getPlan().apply();

    HeadlessRenameResult.Failed failed = assertInstanceOf(second, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.PLAN_STALE, failed.getKind());
    assertNotNull("a plan that already ran says so", failed.getDetail());
  }

  public void testAnUncommittedChangeStopsThePlan() {
    PsiFile file = myFixture.addFileToProject("Pulse.java", """
      public class Pulse {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Pulse");
    HeadlessRenameResult.Planned planned =
      assertInstanceOf(HeadlessRenameProcessor.analyze(getProject(), hits, "count"), HeadlessRenameResult.Planned.class);
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    assertNotNull("no document for the file", document);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "// a note\n"));
    assertTrue("the fixture committed the change, so this test proves nothing",
               PsiDocumentManager.getInstance(getProject()).isUncommited(document));

    HeadlessRenameResult result = planned.getPlan().apply();

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.PLAN_STALE, failed.getKind());
    assertNotNull("the rename wrote to the file", findClass("Pulse").findFieldByName("hits", false));
  }

  public void testARefusedWriteIsReported() {
    myFixture.addFileToProject("Boom.java", """
      public class Boom {
        int boom;
      }
      """);
    PsiField boom = findClass("Boom").findFieldByName("boom", false);
    assertNotNull("no field boom in the fixture", boom);
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME,
                                     ContainerUtil.prepend(HeadlessRenamePsiElementProcessor.EP_NAME.getExtensionList(),
                                                           new RefusingRenameProcessor()),
                                     getTestRootDisposable());

    HeadlessRenameResult result = performRename(boom, "count");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.WRITE_FAILED, failed.getKind());
    assertEquals("The test refuses the write.", failed.getDetail());
    assertNotNull("the rename wrote to the file", findClass("Boom").findFieldByName("boom", false));
  }

  /**
   * A throwable that is not an {@link IncorrectOperationException} reports the same kind.
   * <p>
   * The platform catches only that one type, so any other escapes {@code performRefactoring}. Without
   * the catch of the headless driver it would leave {@link com.intellij.refactoring.rename.RenamePlan}
   * and reach the caller raw.
   */
  public void testAThrowableOfTheWriteIsReported() {
    myFixture.addFileToProject("Crash.java", """
      public class Crash {
        int crash;
      }
      """);
    PsiField crash = findClass("Crash").findFieldByName("crash", false);
    assertNotNull("no field crash in the fixture", crash);
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME,
                                     ContainerUtil.prepend(HeadlessRenamePsiElementProcessor.EP_NAME.getExtensionList(),
                                                           new BreakingRenameProcessor()),
                                     getTestRootDisposable());

    HeadlessRenameResult result = performRename(crash, "count");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.WRITE_FAILED, failed.getKind());
    assertEquals("The test breaks the write.", failed.getDetail());
    assertNotNull("the rename wrote to the file", findClass("Crash").findFieldByName("crash", false));
  }

  public void testAnElementOfTheDefaultProcessorIsRenamed() {
    myFixture.addFileToProject("Plain.java", """
      public class Plain {
        int hits;
        int read() {
          return hits;
        }
      }
      """);
    PsiField hits = findHitsField("Plain");
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME, List.of(), getTestRootDisposable());
    ExtensionTestUtil.maskExtensions(RenamePsiElementProcessorBase.EP_NAME, List.of(), getTestRootDisposable());

    HeadlessRenameResult result = performRename(hits, "count");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    String text = findClass("Plain").getContainingFile().getText();
    assertTrue("the usage kept the old name: " + text, text.contains("return count;"));
  }

  /** A processor that can ask the user, and that carries no headless registration, refuses by name. */
  public void testAProcessorWithNoHeadlessRegistrationIsRefused() {
    myFixture.addFileToProject("Lonely.java", """
      public class Lonely {
        int hits;
      }
      """);
    PsiField hits = findHitsField("Lonely");
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME, List.of(), getTestRootDisposable());

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), hits, "count");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, failed.getKind());
    assertEquals("RenameJavaVariableProcessor renames this element, and it has no headless registration.",
                 failed.getDetail());
    assertNotNull("the field lost its old name", findClass("Lonely").findFieldByName("hits", false));
  }

  /** A language with no {@code headlessRenameSupportedLanguage} statement refuses before the lookup. */
  public void testAnUncoveredLanguageIsRefused() {
    PsiFile notes = myFixture.addFileToProject("notes.txt", "a note\n");

    HeadlessRenameResult result = HeadlessRenameProcessor.analyze(getProject(), notes, "memo.txt");

    HeadlessRenameResult.Failed failed = assertInstanceOf(result, HeadlessRenameResult.Failed.class);
    assertEquals(HeadlessRenameFailure.LANGUAGE_NOT_SUPPORTED, failed.getKind());
    assertEquals("notes.txt was renamed", "notes.txt", notes.getName());
  }

  /**
   * A directory passes the language gate, because its language is {@link Language#ANY}.
   * <p>
   * {@code PsiDirectoryImpl.getLanguage} answers with that one, so a directory names no language the
   * gate could read, and the processor list decides instead.
   */
  public void testADirectoryPassesTheLanguageGate() {
    PsiFile notes = myFixture.addFileToProject("data/notes.txt", "a note\n");
    PsiDirectory data = notes.getContainingDirectory();
    assertNotNull("no directory data in the fixture", data);
    assertEquals("PsiDirectory no longer answers with Language.ANY", Language.ANY, data.getLanguage());

    HeadlessRenameResult result = performRename(data, "info");

    assertInstanceOf(result, HeadlessRenameResult.Applied.class);
    assertEquals("the directory kept the old name", "info", data.getName());
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
    HeadlessRenameResult planned = HeadlessRenameProcessor.analyze(element.getProject(), element, newName);
    HeadlessRenameResult.Planned plan = assertInstanceOf(planned, HeadlessRenameResult.Planned.class);
    return plan.getPlan().apply();
  }

  private PsiField findHitsField(String className) {
    PsiField field = findClass(className).findFieldByName("hits", false);
    assertNotNull("no field hits in " + className, field);
    return field;
  }

  private PsiClass findClass(String name) {
    PsiClass psiClass = myFixture.findClass(name);
    assertNotNull("no class " + name + " in the fixture", psiClass);
    return psiClass;
  }
}
