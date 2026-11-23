// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.SmartPsiFileRange
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.modplan.impl.ModPlanFlow
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.impl.ModPlanFlowUi
import com.intellij.refactoring.modplan.impl.ModPlanTestFlowUi
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamerFactory
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageTarget
import com.intellij.util.containers.MultiMap

/**
 * The rename of the dialog and of the inline rename on the ModPlan engine, from the [RenameProcessor] that they make: the steps that the
 * unit-test mode of the legacy rename does not cover.
 */
class RenameModPlanFlowTest : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    Registry.get("refactoring.rename.modPlan").setValue(true, testRootDisposable)
  }

  fun testRenameOfTheDialogRunsOnModPlan() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo = new Foo(); }")

    assertEquals(ModPlanFlow.Result.DONE, RenameProcessor(project, findClass("Foo"), "Bar", false, false).runOnModPlan(false))

    assertEquals("public class User { Bar foo = new Bar(); }", user.text)
    assertNotNull(myFixture.findFileInTempDir("Bar.java"))
  }

  /** As in the legacy rename, an element of `addElement` is renamed with the primary element. */
  fun testElementOfAddElementIsRenamedWithThePrimaryElement() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    myFixture.addFileToProject("Other.java", "public class Other {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo; Other other; }")
    val processor = RenameProcessor(project, findClass("Foo"), "Bar", false, false)
    processor.addElement(findClass("Other"), "Another")

    assertEquals(ModPlanFlow.Result.DONE, processor.runOnModPlan(false))

    assertEquals("public class User { Bar foo; Another other; }", user.text)
    assertNotNull(myFixture.findFileInTempDir("Another.java"))
  }

  /** The rename of `RefactoringFactory` runs on the engine, with the elements of `addElement`. */
  fun testRenameOfTheRefactoringFactoryRunsOnModPlan() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    myFixture.addFileToProject("Other.java", "public class Other {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo; Other other; }")
    val previewedOffsets = previewExcluding(excludedOffset = -1)
    val rename = RefactoringFactory.getInstance(project).createRename(findClass("Foo"), "Bar", false, false)
    rename.addElement(findClass("Other"), "Another")
    rename.setPreviewUsages(true)

    rename.run()

    // Only the flow of the engine shows the preview of the test UI.
    assertNotEmpty(previewedOffsets.usageOffsets)
    assertEquals("public class User { Bar foo; Another other; }", user.text)
  }

  /** The dialog renames what the user chose before it: an override alone, and not its base method as a headless rename would. */
  fun testOverrideThatTheUserChoseIsRenamedAlone() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} }")
    myFixture.addFileToProject("Sub.java", "public class Sub extends Worker { @Override public void run() {} }")

    val override = findClass("Sub").findMethodsByName("run", false).single()

    assertEquals(ModPlanFlow.Result.DONE, RenameProcessor(project, override, "execute", false, false).runOnModPlan(false))

    assertSize(1, findClass("Worker").findMethodsByName("run", false))
    assertSize(1, findClass("Sub").findMethodsByName("execute", false))
  }

  fun testRenamerThatTheUserTurnedOnRenamesItsElements() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo = new Foo(); }")
    val processor = RenameProcessor(project, findClass("Foo"), "Bar", false, false)
    processor.addRenamerFactory(AutomaticRenamerFactory.EP_NAME.findExtensionOrFail(AutomaticVariableRenamerFactory::class.java))

    assertEquals(ModPlanFlow.Result.DONE, processor.runOnModPlan(false))

    assertEquals("public class User { Bar bar = new Bar(); }", user.text)
  }

  /** As in the legacy rename, the setting of the automatic renaming dialog that is off turns off every renamer. */
  fun testRenamerThatTheUserTurnedOnRenamesNothingWhenTheAutomaticRenamingIsOff() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo = new Foo(); }")
    val settings = RefactoringSettings.getInstance()
    val oldValue = settings.RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG
    settings.RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG = false
    Disposer.register(testRootDisposable) { settings.RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG = oldValue }
    val processor = RenameProcessor(project, findClass("Foo"), "Bar", false, false)
    processor.addRenamerFactory(AutomaticRenamerFactory.EP_NAME.findExtensionOrFail(AutomaticVariableRenamerFactory::class.java))

    assertEquals(ModPlanFlow.Result.DONE, processor.runOnModPlan(false))

    assertEquals("public class User { Bar foo = new Bar(); }", user.text)
  }

  /** The engine searches the scope of the dialog, as the legacy rename does: a usage out of it keeps the old name. */
  fun testRenameInALocalScopeKeepsTheUsagesOutOfTheScope() {
    val foo = myFixture.addFileToProject("Foo.java", "public class Foo { Foo self; }")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo; }")
    val processor = RenameProcessor(project, findClass("Foo"), "Bar", LocalSearchScope(foo), false, false)

    assertEquals(ModPlanFlow.Result.DONE, processor.runOnModPlan(false))

    assertEquals("public class Bar { Bar self; }", foo.text)
    assertEquals("public class User { Foo foo; }", user.text)
  }

  /** As in the preview of the legacy rename, the user leaves an occurrence in a comment out; the others are renamed. */
  fun testOccurrenceThatTheUserLeavesOutInThePreviewKeepsItsName() {
    val bean = myFixture.addFileToProject("Bean.java", """
      public class Bean {
        // the count of items
        private int count;
        // count again
        int get() { return count; }
      }
      """.trimIndent())
    val renamedOffset = bean.text.indexOf("count of")
    val excludedOffset = bean.text.indexOf("count again")
    val previewedOffsets = previewExcluding(excludedOffset)

    // The preview of the test UI goes on at once, but the flow returns when it shows a preview.
    assertEquals(ModPlanFlow.Result.PREVIEW_SHOWN,
                 RenameProcessor(project, findClass("Bean").findFieldByName("count", false)!!, "total", true, false).runOnModPlan(true))

    assertContainsElements(previewedOffsets.usageOffsets, renamedOffset, excludedOffset)
    assertEquals("""
      public class Bean {
        // the total of items
        private int total;
        // count again
        int get() { return total; }
      }
      """.trimIndent(), bean.text)
  }

  /** As the legacy rename does, an occurrence in a comment shows the preview also if the user did not press Preview. */
  fun testOccurrenceInACommentShowsThePreviewWithoutPreviewButton() {
    val bean = myFixture.addFileToProject("Bean.java", """
      public class Bean {
        // the count of items
        private int count;
        int get() { return count; }
      }
      """.trimIndent())
    val commentOffset = bean.text.indexOf("count of")
    val previewedOffsets = previewExcluding(excludedOffset = -1)

    assertEquals(ModPlanFlow.Result.PREVIEW_SHOWN,
                 RenameProcessor(project, findClass("Bean").findFieldByName("count", false)!!, "total", true, false).runOnModPlan(false))

    assertEquals(listOf(commentOffset), previewedOffsets.nonCodeOffsets)
    assertEquals("""
      public class Bean {
        // the total of items
        private int total;
        int get() { return total; }
      }
      """.trimIndent(), bean.text)
  }

  /** Without an occurrence in a comment, the rename writes with no preview, as the legacy rename does. */
  fun testRenameWithNoOccurrenceInACommentShowsNoPreview() {
    myFixture.addFileToProject("Bean.java", "public class Bean { private int count; int get() { return count; } }")
    val previewedOffsets = previewExcluding(excludedOffset = -1)

    assertEquals(ModPlanFlow.Result.DONE,
                 RenameProcessor(project, findClass("Bean").findFieldByName("count", false)!!, "total", true, false).runOnModPlan(false))

    assertEmpty(previewedOffsets.usageOffsets)
  }

  fun testUndoOfTheRenameRestoresEveryFileInOneStep() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", "public class User { Foo foo = new Foo(); }")
    TestDialogManager.setTestDialog(TestDialog.OK, testRootDisposable)
    assertEquals(ModPlanFlow.Result.DONE, RenameProcessor(project, findClass("Foo"), "Bar", false, false).runOnModPlan(false))

    UndoManager.getInstance(project).undo(null)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    assertEquals("public class User { Foo foo = new Foo(); }", user.text)
    assertEquals("public class Foo {}", findClass("Foo").containingFile.text)
    assertNull(myFixture.findFileInTempDir("Bar.java"))
  }

  fun testCancelInTheConflictsDialogWritesNothing() {
    val worker = myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} public void execute() {} }")
    val shownConflicts = ArrayList<String>()
    ApplicationManager.getApplication().replaceService(ModPlanFlowUi::class.java, object : ModPlanTestFlowUi() {
      override fun confirmConflicts(project: Project, conflicts: MultiMap<PsiElement, String>): Boolean {
        shownConflicts.addAll(conflicts.values())
        return false
      }
    }, testRootDisposable)
    val run = findClass("Worker").findMethodsByName("run", false).single()

    assertEquals(ModPlanFlow.Result.CANCELLED, RenameProcessor(project, run, "execute", false, false).runOnModPlan(false))

    assertNotEmpty(shownConflicts)
    assertEquals("public class Worker { public void run() {} public void execute() {} }", worker.text)
  }

  /** The offsets that the preview showed. */
  private class PreviewedOffsets {
    val usageOffsets = ArrayList<Int>()
    val nonCodeOffsets = ArrayList<Int>()
  }

  /** Replaces the UI with one whose preview goes on at once and leaves out the usage at [excludedOffset]. */
  private fun previewExcluding(excludedOffset: Int): PreviewedOffsets {
    val previewedOffsets = PreviewedOffsets()
    ApplicationManager.getApplication().replaceService(ModPlanFlowUi::class.java, object : ModPlanTestFlowUi() {
      override fun previewUsages(
        project: Project,
        refactoring: ModPlanRefactoring,
        targets: Array<out UsageTarget>,
        plan: ModPlan.AcceptedPlan,
        nonCodeUsages: Array<UsageInfo>,
        proceed: (previewAgain: Boolean, excludedUsages: Set<SmartPsiFileRange>) -> Unit,
      ): Boolean {
        previewedOffsets.usageOffsets.addAll(plan.usages.mapNotNull { it.range?.startOffset })
        previewedOffsets.nonCodeOffsets.addAll(plan.conflicts.changedNonCodeUsages().mapNotNull { it.location?.range?.startOffset })
        proceed(false, plan.usages.filter { it.range?.startOffset == excludedOffset }.toSet())
        return true
      }
    }, testRootDisposable)
    return previewedOffsets
  }

  private fun findClass(name: String): PsiClass = JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.projectScope(project))!!
}
