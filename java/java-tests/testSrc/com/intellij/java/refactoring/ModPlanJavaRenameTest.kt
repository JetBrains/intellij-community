// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLabeledStatement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiParameter
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl.RenamedClassForm
import com.intellij.psi.impl.source.resolve.RenamedClassView
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.scope.EditorSelectionLocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring
import com.intellij.refactoring.rename.HeadlessRenamePsiElementProcessor
import com.intellij.refactoring.rename.ModPlanJavaRenameOptions
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiDirectoryProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.rename.naming.AutomaticVariableRenamerFactory
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ModPlanRenameTestSupport
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.util.concurrent.Callable

/**
 * The rename of Java elements on the ModPlan engine. The analysis runs on a background thread, as the MCP tool runs it.
 *
 * The usages in other files matter most here: an edit writes a copy of one file, and a reference in that copy resolves into the other files
 * as they were before the rename.
 */
class ModPlanJavaRenameTest : LightJavaCodeInsightFixtureTestCase() {

  fun testLocalScopeFindsTheUsagesOfTheLegacyRename() {
    myFixture.configureByText("Main.java", SCOPE_TEXT)
    val statement = PsiTreeUtil.findChildrenOfType(myFixture.file, com.intellij.psi.PsiExpressionStatement::class.java).first()

    val legacy = renamedByLegacy { LocalSearchScope(statement) }
    val modPlan = renamedByModPlan { LocalSearchScope(statement) }

    assertEquals(legacy, modPlan)
    assertEquals(2, "renamed(".toRegex(RegexOption.LITERAL).findAll(modPlan).count())
  }

  fun testEditorSelectionScopeFindsTheUsagesOfTheLegacyRename() {
    myFixture.configureByText("Main.java", SCOPE_TEXT)
    val selectionStart = myFixture.file.text.indexOf("m();")
    myFixture.editor.selectionModel.setSelection(selectionStart, selectionStart + "m();".length)

    val legacy = renamedByLegacy { EditorSelectionLocalSearchScope(myFixture.editor, project, "Selection") }
    val modPlan = renamedByModPlan { EditorSelectionLocalSearchScope(myFixture.editor, project, "Selection") }

    assertEquals(legacy, modPlan)
    assertEquals(2, "renamed(".toRegex(RegexOption.LITERAL).findAll(modPlan).count())
  }

  private fun renamedByLegacy(scope: () -> SearchScope): String {
    val original = myFixture.file.text
    val method = runReadActionBlocking { findClass("Main").findMethodsByName("m", false).single() }
    RenameProcessor(project, method, "renamed", scope(), false, false).run()
    val renamed = myFixture.file.text
    WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(original) }
    com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
    return renamed
  }

  private fun renamedByModPlan(scope: () -> SearchScope): String {
    val method = runReadActionBlocking { findClass("Main").findMethodsByName("m", false).single() }
    val request = runReadActionBlocking {
      val values = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.NEW_NAME, "renamed").with(ModPlanRenameOptions.SUBSTITUTE_TARGET, true)
      ModPlanRequest(project, listOf(com.intellij.model.SymbolPointer.of(method)), values, ModPlanRequest.searchScopeSupplier(scope()))
    }
    val ready = inBackground { ModPlanRenameRefactoring.getInstance().plan(request) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)
    return myFixture.file.text
  }

  fun testMethodWithUsagesInTwoFilesAndOverride() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void run() {}
      }
      """.trimIndent())
    val sub = myFixture.addFileToProject("Sub.java", """
      public class Sub extends Worker {
        @Override
        public void run() {}
      }
      """.trimIndent())
    val main = myFixture.addFileToProject("Main.java", """
      public class Main {
        void f(Worker w, Sub s) {
          w.run();
          s.run();
        }
      }
      """.trimIndent())

    rename(runMethod("Worker"), "execute")

    assertSize(1, findClass("Worker").findMethodsByName("execute", false))
    assertEquals("""
      public class Sub extends Worker {
        @Override
        public void execute() {}
      }
      """.trimIndent(), sub.text)
    assertEquals("""
      public class Main {
        void f(Worker w, Sub s) {
          w.execute();
          s.execute();
        }
      }
      """.trimIndent(), main.text)
  }

  fun testOverrideRenamesItsBaseMethodByDefault() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} }")
    myFixture.addFileToProject("Sub.java", "public class Sub extends Worker { @Override public void run() {} }")

    rename(runMethod("Sub"), "execute")

    assertSize(1, findClass("Worker").findMethodsByName("execute", false))
    assertSize(1, findClass("Sub").findMethodsByName("execute", false))
  }

  fun testOverrideIsRenamedAloneWhenTheBaseMethodIsNot() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} }")
    myFixture.addFileToProject("Sub.java", "public class Sub extends Worker { @Override public void run() {} }")
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, runMethod("Sub"), "execute", substitute = true, ModPlanOptionValues.EMPTY) }

    val question = askedQuestions(request).first()
    rename(runMethod("Sub"), "execute", ModPlanOptionValues.EMPTY.with(ModPlanJavaRenameOptions.RENAME_BASE_METHOD, false))

    assertEquals(listOf<ModPlanOption<*>>(ModPlanJavaRenameOptions.RENAME_BASE_METHOD), (question as ModPlanQuestion.Custom).options())
    assertSize(1, findClass("Worker").findMethodsByName("run", false))
    assertSize(1, findClass("Sub").findMethodsByName("execute", false))
  }

  fun testSiblingThatASubclassInheritsForAnInterfaceAsksAboutTheBaseMethod() {
    myFixture.addFileToProject("Runner.java", "public interface Runner { void run(); }")
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} }")
    myFixture.addFileToProject("Sub.java", "public class Sub extends Worker implements Runner {}")
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, runMethod("Worker"), "execute", substitute = true, ModPlanOptionValues.EMPTY) }

    val questions = askedQuestions(request)
    rename(runMethod("Worker"), "execute", ModPlanOptionValues.EMPTY.with(ModPlanJavaRenameOptions.RENAME_BASE_METHOD, false))

    assertEquals(listOf<ModPlanOption<*>>(ModPlanJavaRenameOptions.RENAME_BASE_METHOD), (questions.first() as ModPlanQuestion.Custom).options())
    assertSize(1, findClass("Runner").findMethodsByName("run", false))
    assertSize(1, findClass("Worker").findMethodsByName("execute", false))
  }

  fun testClassUsedInAnotherFileIsRenamedWithItsFile() {
    myFixture.addFileToProject("p/Old.java", """
      package p;

      public class Old {
        public Old() {}
      }
      """.trimIndent())
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import p.Old;

      public class User {
        Old o = new Old();
      }
      """.trimIndent())

    rename(findClass("p.Old"), "New")

    val renamed = findClass("p.New")
    assertEquals("New.java", renamed.containingFile.name)
    assertEquals("""
      package p;

      public class New {
        public New() {}
      }
      """.trimIndent(), renamed.containingFile.text)
    assertEquals("""
      package q;

      import p.New;

      public class User {
        New o = new New();
      }
      """.trimIndent(), user.text)
  }

  /** The legacy rename qualifies the call after the write, when the call resolves to the method of the inner class. */
  fun testCallHiddenByAMethodOfAnInnerClassInAnotherFileIsQualified() {
    myFixture.addFileToProject("A.java", """
      public class A {
        public void run() {}
      }
      """.trimIndent())
    val outer = myFixture.addFileToProject("Outer.java", """
      public class Outer extends A {
        class Inner {
          void execute() {}
          void f() { run(); }
        }
      }
      """.trimIndent())

    rename(runMethod("A"), "execute")

    assertEquals("""
      public class Outer extends A {
        class Inner {
          void execute() {}
          void f() { Outer.this.execute(); }
        }
      }
      """.trimIndent(), outer.text)
  }

  /** The legacy rename keeps the referent of the references in the file of the class, which the class with the new name would take. */
  fun testClassRenamedToTheNameOfAnotherClassQualifiesItsReferences() {
    val old = myFixture.addFileToProject("Old.java", """
      public class Old {
        String s;
        String get() { return String.valueOf(1); }
      }
      """.trimIndent())

    rename(findClass("Old"), "String")

    assertEquals("""
      public class String {
        java.lang.String s;
        java.lang.String get() { return java.lang.String.valueOf(1); }
      }
      """.trimIndent(), old.text)
  }

  /**
   * The legacy rename binds the references after the write, which qualifies the ones that a class of the new name takes, and deletes the
   * import that no reference uses then.
   */
  fun testReferenceInAnotherFileThatAnInnerClassOfTheNewNameTakesIsQualified() {
    myFixture.addFileToProject("p/Old.java", """
      package p;

      public class Old {
        public Old() {}
      }
      """.trimIndent())
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import p.Old;

      public class User {
        static class New {}
        Old o = new Old();
      }
      """.trimIndent())

    rename(findClass("p.Old"), "New")

    assertEquals("""
      package q;

      public class User {
        static class New {}
        p.New o = new p.New();
      }
      """.trimIndent(), user.text)
  }

  /** The decision about the import is made again for the references that are left: no reference is qualified, so the import stays. */
  fun testImportStaysWhenTheExcludedReferencesWereTheOnesToQualify() {
    myFixture.addFileToProject("p/Old.java", """
      package p;

      public class Old {
        public Old() {}
      }
      """.trimIndent())
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import p.Old;

      public class User {
        static class New {}
        Old o = new Old();
      }
      """.trimIndent())
    val excludedPlaces = Regex("Old").findAll(user.text).drop(1).map { match ->
      SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(user, TextRange.from(match.range.first, "Old".length))
    }.toList()

    plan(findClass("p.Old"), "New")
    val ready = inBackground { planWithout(excludedPlaces) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertSize(2, excludedPlaces)
    assertEquals("""
      package q;

      import p.New;

      public class User {
        static class New {}
        Old o = new Old();
      }
      """.trimIndent(), user.text)
  }

  /** The legacy rename qualifies a reference to a static import that the renamed variable hides, and imports the class of the member. */
  fun testVariableHidingAStaticImportQualifiesItsUsagesWithAnImport() {
    val user = myFixture.addFileToProject("User.java", """
      import static java.io.File.separatorChar;

      public class User {
        void f() {
          int local = 0;
          int c = separatorChar;
        }
      }
      """.trimIndent())
    val local = PsiTreeUtil.findChildrenOfType(user, PsiLocalVariable::class.java).first { it.name == "local" }

    rename(local, "separatorChar")

    assertEquals("""
      import java.io.File;

      public class User {
        void f() {
          int separatorChar = 0;
          int c = File.separatorChar;
        }
      }
      """.trimIndent(), user.text)
  }

  /** No processor claims a label, so the default processor renames it, as in the legacy rename. */
  fun testLabelIsRenamedWithItsBreaks() {
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        void f() {
          outer: for (int i = 0; i < 10; i++) {
            if (i == 5) continue outer;
            break outer;
          }
        }
      }
      """.trimIndent())

    rename(PsiTreeUtil.findChildOfType(user, PsiLabeledStatement::class.java)!!, "loop")

    assertEquals("""
      public class User {
        void f() {
          loop: for (int i = 0; i < 10; i++) {
            if (i == 5) continue loop;
            break loop;
          }
        }
      }
      """.trimIndent(), user.text)
  }

  fun testLambdaAndCatchParametersAreRenamed() {
    val user = myFixture.addFileToProject("User.java", """
      import java.util.function.IntUnaryOperator;

      public class User {
        void f() {
          IntUnaryOperator op = x -> x + 1;
          try {
            op.applyAsInt(1);
          }
          catch (RuntimeException e) {
            throw e;
          }
        }
      }
      """.trimIndent())
    fun parameter(name: String) = PsiTreeUtil.findChildrenOfType(user, PsiParameter::class.java).first { it.name == name }

    rename(parameter("x"), "value")
    rename(parameter("e"), "error")

    assertEquals("""
      import java.util.function.IntUnaryOperator;

      public class User {
        void f() {
          IntUnaryOperator op = value -> value + 1;
          try {
            op.applyAsInt(1);
          }
          catch (RuntimeException error) {
            throw error;
          }
        }
      }
      """.trimIndent(), user.text)
  }

  fun testCommentIsRenamedWhenTheOptionSaysSo() {
    val bean = myFixture.addFileToProject("Bean.java", """
      public class Bean {
        // the count of items
        private int count;
        int get() { return count; }
      }
      """.trimIndent())

    rename(findClass("Bean").findFieldByName("count", false)!!, "total", ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true))

    assertEquals("""
      public class Bean {
        // the total of items
        private int total;
        int get() { return total; }
      }
      """.trimIndent(), bean.text)
  }

  fun testAnExcludedOccurrenceInCommentsKeepsItsName() {
    val bean = myFixture.addFileToProject("Bean.java", """
      public class Bean {
        // the count of items
        private int count;
        // count again
        int get() { return count; }
      }
      """.trimIndent())
    val excludedPlace = SmartPointerManager.getInstance(project)
      .createSmartPsiFileRangePointer(bean, TextRange.from(bean.text.indexOf("count again"), "count".length))
    val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true)
    val field = findClass("Bean").findFieldByName("count", false)!!

    val plan = plan(field, "total", options) as ModPlan.AcceptedPlan
    val ready = inBackground { planWithout(listOf(excludedPlace)) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertContainsElements(plan.usages, excludedPlace)
    assertDoesntContain(ready.usages, excludedPlace)
    assertEquals("""
      public class Bean {
        // the total of items
        private int total;
        // count again
        int get() { return total; }
      }
      """.trimIndent(), bean.text)
  }

  /** A caller that asked what is renamed with its own UI gets it renamed as it is: here the override, not its base method. */
  fun testRequestedTargetIsRenamedAsItIs() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() {} }")
    myFixture.addFileToProject("Sub.java", "public class Sub extends Worker { @Override public void run() {} }")
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, runMethod("Sub"), "execute", substitute = false, ModPlanOptionValues.EMPTY) }

    val questions = askedQuestions(request)
    val ready = inBackground { ModPlanRenameRefactoring.getInstance().plan(request) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertDoesntContain(questions.filterIsInstance<ModPlanQuestion.Custom>().flatMap { it.options() }, ModPlanJavaRenameOptions.RENAME_BASE_METHOD)
    assertSize(1, findClass("Worker").findMethodsByName("run", false))
    assertSize(1, findClass("Sub").findMethodsByName("execute", false))
  }

  fun testCommentIsKeptByDefault() {
    val bean = myFixture.addFileToProject("Bean.java", """
      public class Bean {
        // the count of items
        private int count;
        int get() { return count; }
      }
      """.trimIndent())

    rename(findClass("Bean").findFieldByName("count", false)!!, "total")

    assertEquals("""
      public class Bean {
        // the count of items
        private int total;
        int get() { return total; }
      }
      """.trimIndent(), bean.text)
  }

  fun testVariableNamedAfterTheClassKeepsItsNameByDefault() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        void f() { Foo foo = new Foo(); }
      }
      """.trimIndent())

    rename(findClass("Foo"), "Bar")

    assertEquals("""
      public class User {
        void f() { Bar foo = new Bar(); }
      }
      """.trimIndent(), user.text)
  }

  fun testVariableNamedAfterTheClassIsRenamedWhenTheSwitchIsOn() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        void f() { Foo foo = new Foo(); }
      }
      """.trimIndent())

    rename(findClass("Foo"), "Bar", relatedOn().with(variablesSwitch(), true))

    assertEquals("""
      public class User {
        void f() { Bar bar = new Bar(); }
      }
      """.trimIndent(), user.text)
  }

  fun testOnlyTheChosenRelatedRenamesAreMadeWithTheirNames() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        Foo myFoo;
        void f() { Foo foo = new Foo(); }
      }
      """.trimIndent())
    val myFoo = findClass("User").findFieldByName("myFoo", false)!!
    val chosen = runReadActionBlocking { ModPlanRenameOptions.relatedRename(myFoo, "myBaz") }!!

    rename(findClass("Foo"), "Bar", relatedOn().with(variablesSwitch(), true)
      .with(ModPlanRenameOptions.CHOOSE_RELATED, true).with(ModPlanRenameOptions.RELATED_RENAMES, chosen))

    assertEquals("""
      public class User {
        Bar myBaz;
        void f() { Bar foo = new Bar(); }
      }
      """.trimIndent(), user.text)
  }

  /** As the legacy rename, ModPlan leaves out an automatic rename whose new name collides, with no conflict, and renames the rest. */
  fun testRelatedRenameThatCollidesIsLeftOut() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        void f() { Foo foo = new Foo(); int bar = 0; }
      }
      """.trimIndent())

    val ready = assertInstanceOf(plan(findClass("Foo"), "Bar", relatedOn().with(variablesSwitch(), true)), ModPlan.AcceptedPlan::class.java)

    assertEmpty(ready.conflicts.conflicts)
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)
    assertEquals("""
      public class User {
        void f() { Bar foo = new Bar(); int bar = 0; }
      }
      """.trimIndent(), user.text)
  }

  /** A renamer with no option, such as the one of the constructor parameter of a field, runs with the related renames, as in the legacy rename. */
  fun testConstructorParameterOfARenamedFieldIsRenamed() {
    val point = myFixture.addFileToProject("Point.java", """
      public class Point {
        private final int x;
        Point(int x) { this.x = x; }
      }
      """.trimIndent())

    rename(findClass("Point").findFieldByName("x", false)!!, "left", relatedOn())

    assertEquals("""
      public class Point {
        private final int left;
        Point(int left) { this.left = left; }
      }
      """.trimIndent(), point.text)
  }

  /** A caller with no user renames the element alone: the related renames are off by default. */
  fun testRelatedElementsKeepTheirNamesByDefault() {
    val point = myFixture.addFileToProject("Point.java", """
      public class Point {
        private final int x;
        Point(int x) { this.x = x; }
      }
      """.trimIndent())

    rename(findClass("Point").findFieldByName("x", false)!!, "left")

    assertEquals("""
      public class Point {
        private final int left;
        Point(int x) { this.left = x; }
      }
      """.trimIndent(), point.text)
  }

  fun testTheOptionsOfTheFactoriesAreAskedBeforeTheRelatedRenames() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    myFixture.addFileToProject("User.java", """
      public class User {
        void f() { Foo foo = new Foo(); }
      }
      """.trimIndent())
    val request = runReadActionBlocking { ModPlanRenameRefactoring.request(project, findClass("Foo"), "Bar", substitute = true, ModPlanOptionValues.EMPTY) }

    val asked = askedQuestions(request) { option -> option == ModPlanRenameOptions.RENAME_RELATED || option == variablesSwitch() }

    assertContainsElements(asked.filterIsInstance<ModPlanQuestion.Form>().flatMap { it.options() },
                           ModPlanRenameOptions.RENAME_RELATED, variablesSwitch())
    assertContainsElements(asked.filterIsInstance<ModPlanQuestion.Custom>().flatMap { it.options() },
                           ModPlanRenameOptions.CHOOSE_RELATED, ModPlanRenameOptions.RELATED_RENAMES)
    assertTrue(asked.indexOfFirst { it is ModPlanQuestion.Custom } > asked.indexOfLast { it is ModPlanQuestion.Form })
  }

  fun testJavaModuleIsRenamed() {
    val moduleInfo = myFixture.addFileToProject("module-info.java", "module old.name { }") as PsiJavaFile

    rename(moduleInfo.moduleDeclaration!!, "other.name")

    assertEquals("module other.name { }", moduleInfo.text)
  }

  fun testPackageIsRenamedWithItsDirectory() {
    myFixture.addFileToProject("pack1/Widget.java", "package pack1;\n\npublic class Widget {}")

    rename(findPackage("pack1"), "pack2")

    val widget = findClass("pack2.Widget").containingFile as PsiJavaFile
    assertEquals("pack2", widget.packageName)
    assertEquals("pack2", widget.containingDirectory!!.name)
  }

  /** A directory of a package is renamed as the package, as DirectoryAsPackageRenameHandler does for a package of one directory. */
  fun testPackageDirectoryIsRenamedAsItsPackage() {
    val wheel = myFixture.addFileToProject("pack6/Wheel.java", "package pack6;\n\npublic class Wheel {}")

    rename(wheel.containingDirectory!!, "pack7")

    val moved = findClass("pack7.Wheel").containingFile as PsiJavaFile
    assertEquals("pack7", moved.packageName)
    assertEquals("pack7", moved.containingDirectory!!.name)
  }

  /**
   * The provider of a directory renames it as its package with no headless processor. A processor of another language, such as the Go one,
   * may claim every directory before the Java one, so the Java processor comes first here.
   */
  fun testDirectoryOfOnePackageIsRenamedAsThePackage() {
    ExtensionTestUtil.maskExtensions(HeadlessRenamePsiElementProcessor.EP_NAME, emptyList(), testRootDisposable)
    val processors = RenamePsiElementProcessorBase.EP_NAME.extensionList
    val directoryProcessor = processors.first { it.javaClass == RenamePsiDirectoryProcessor::class.java }
    ExtensionTestUtil.maskExtensions(RenamePsiElementProcessorBase.EP_NAME, listOf(directoryProcessor) + (processors - directoryProcessor),
                                     testRootDisposable)
    val a = myFixture.addFileToProject("p/A.java", "package p; public class A {}")
    val main = myFixture.addFileToProject("Main.java", "import p.A; class Main { A a; }")

    ModPlanRenameTestSupport.renameOnModPlan(project, a.containingDirectory!!, "q", ModPlanOptionValues.EMPTY)

    assertEquals("import q.A; class Main { A a; }", main.text)
    assertTrue(findClass("q.A").containingFile.text.startsWith("package q;"))
  }

  fun testInvalidPackageNameIsRefused() {
    myFixture.addFileToProject("pack3/Meter.java", "package pack3;\n\npublic class Meter {}")

    val refused = assertInstanceOf(plan(findPackage("pack3"), "1bad"), ModPlan.RefusedPlan::class.java)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    assertNotNull("a refusal must name its reason", refused.message)
  }

  /** A qualified name with the parent of the package renames it to the short name, as the rename dialog does. */
  fun testQualifiedPackageNameWithTheSameParentIsRenamed() {
    myFixture.addFileToProject("outer/inner/Gear.java", "package outer.inner;\n\npublic class Gear {}")

    rename(findPackage("outer.inner"), "outer.renamed")

    assertEquals("outer.renamed", (findClass("outer.renamed.Gear").containingFile as PsiJavaFile).packageName)
  }

  /** A qualified name with another parent asks for a move, which the rename does not do. */
  fun testQualifiedPackageNameThatMovesIsAConflict() {
    myFixture.addFileToProject("pack8/Axle.java", "package pack8;\n\npublic class Axle {}")

    val accepted = assertInstanceOf(plan(findPackage("pack8"), "other.pack9"), ModPlan.AcceptedPlan::class.java)

    assertTrue("a move must be a conflict", accepted.conflicts.needsConfirmation())
    assertEmpty("a move must edit nothing", accepted.execution.affectedFiles)
  }

  fun testInvalidNameIsRefused() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void run() {}
      }
      """.trimIndent())

    val refused = assertInstanceOf(plan(runMethod("Worker"), "1run"), ModPlan.RefusedPlan::class.java)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
  }

  fun testExistingMethodWithTheNewNameIsABrokenConflict() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void run() {}
        public void execute() {}
      }
      """.trimIndent())

    val ready = assertInstanceOf(plan(runMethod("Worker"), "execute"), ModPlan.AcceptedPlan::class.java)

    assertTrue(ready.conflicts.conflicts.any { it.kind == ModPlanConflictKind.BROKEN })
  }

  /** As bindToElement in the legacy rename: a reference that the package qualifies is shortened, and the file gets one import. */
  fun testPackageQualifiedReferencesInOneFileGetOneImport() {
    myFixture.addFileToProject("p/Old.java", "package p;\n\npublic class Old {}")
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      public class User {
        p.Old a;
        p.Old b = new p.Old();
      }
      """.trimIndent())

    rename(findClass("p.Old"), "New")

    assertEquals("""
      package q;

      import p.New;

      public class User {
        New a;
        New b = new New();
      }
      """.trimIndent(), user.text)
  }

  fun testSingleImportStaysForAPackageQualifiedReferenceThatGetsTheShortName() {
    myFixture.addFileToProject("p/Old.java", "package p;\n\npublic class Old {}")
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import p.Old;

      public class User {
        p.Old a;
        class Inner {
          class New {}
          Old b;
        }
      }
      """.trimIndent())

    rename(findClass("p.Old"), "New")

    assertEquals("""
      package q;

      import p.New;

      public class User {
        New a;
        class Inner {
          class New {}
          p.New b;
        }
      }
      """.trimIndent(), user.text)
  }

  fun testExcludedOnlyPackageQualifiedReferenceAddsNoImport() {
    myFixture.addFileToProject("p/Old.java", "package p;\n\npublic class Old {}")
    val userText = """
      package q;

      public class User {
        p.Old a;
      }
      """.trimIndent()
    val user = myFixture.addFileToProject("q/User.java", userText)

    val plan = plan(findClass("p.Old"), "New") as ModPlan.AcceptedPlan
    val excludedPlaces = placesAt(plan, user, user.text.indexOf("Old a"))
    val ready = inBackground { planWithout(excludedPlaces) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertSize(1, excludedPlaces)
    assertEquals(userText, user.text)
    assertNotNull(findClass("p.New"))
  }

  /** A reference that the package qualifies in the package of the class needs no import: it gets the short name. */
  fun testPackageQualifiedReferenceInThePackageOfTheClassGetsTheShortName() {
    myFixture.addFileToProject("p/Old.java", "package p;\n\npublic class Old {}")
    val user = myFixture.addFileToProject("p/User.java", """
      package p;

      public class User {
        p.Old a;
      }
      """.trimIndent())

    rename(findClass("p.Old"), "New")

    assertEquals("""
      package p;

      public class User {
        New a;
      }
      """.trimIndent(), user.text)
  }

  /** In the file of the class the copy of the class has the new name already when the reference binds. */
  fun testReferenceToTheClassInItsOwnFileGetsTheNewName() {
    val file = myFixture.addFileToProject("p/Loader.java", """
      package p;

      class Loader {
        Loader next;
        p.Loader other;
      }
      """.trimIndent())

    rename(findClass("p.Loader"), "Reader")

    assertEquals("""
      package p;

      class Reader {
        Reader next;
        Reader other;
      }
      """.trimIndent(), file.text)
  }

  /** The delete of the colliding import and the edit of another reference of the file come before the bind of the qualified reference. */
  fun testEarlierEditsOfTheFileComeBeforeTheBind() {
    myFixture.addFileToProject("pack1/MyList.java", "package pack1;\n\npublic class MyList {}")
    val usage = myFixture.addFileToProject("pack2/Usage.java", """
      package pack2;

      import java.util.List;
      import pack1.*;

      class Usage {
        List list;
        MyList a;
        pack1.MyList b;
      }
      """.trimIndent())

    rename(findClass("pack1.MyList"), "List")

    assertEquals("""
      package pack2;

      import pack1.*;

      class Usage {
        java.util.List list;
        List a;
        List b;
      }
      """.trimIndent(), usage.text)
  }

  /**
   * The plan decides the form on the code before the write, with the colliding import ignored. The write decides it again in the copy,
   * where that import is deleted. Both forms are the same in the data of [RenameClassTest.testCollision].
   */
  fun testFormOfThePlanIsTheFormOfTheWrite() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameClass/collision"
    myFixture.copyDirectoryToProject("before", "")
    val aClass = findClass("pack1.MyList")
    val expectedForms = mapOf("Usage" to listOf(RenamedClassForm.RENAMED),
                              "Usage2" to listOf(RenamedClassForm.RENAMED),
                              "Usage3" to listOf(RenamedClassForm.IMPORTED))
    for ([className, expected] in expectedForms) {
      val file = findClass("pack2.$className").containingFile as PsiJavaFile
      val references = PsiTreeUtil.findChildrenOfType(file, PsiJavaCodeReferenceElement::class.java).filter { it.referenceName == "MyList" }
      val collidingImports = listOfNotNull(file.importList!!.findSingleClassImportStatement("java.util.List"))
      val planView = RenamedClassView(aClass, "List", collidingImports, null)
      val planForms = references.map { PsiJavaCodeReferenceElementImpl.formAfterRename(it, planView) }
      val writeForms = ArrayList<RenamedClassForm>()
      ModCommand.psiUpdate(ActionContext.from(null, file)) { updater ->
        val copies = references.map { updater.getWritable(it) }
        collidingImports.map { updater.getWritable(it) }.forEach { it.delete() }
        copies.mapTo(writeForms) { PsiJavaCodeReferenceElementImpl.formAfterRename(it, RenamedClassView(aClass, "List", listOf(), null)) }
      }
      assertEquals(className, expected, planForms)
      assertEquals(className, planForms, writeForms)
    }
  }

  /** A reference that a field of an inner class takes under the new name is qualified; the excluded one keeps the old text. */
  fun testExcludedReferenceHiddenByAFieldOfTheNewNameKeepsItsText() {
    val outer = myFixture.addFileToProject("Outer.java", """
      public class Outer {
        int count;
        class Inner {
          int total;
          int get() { return count; }
          int twice() { return count * 2; }
        }
      }
      """.trimIndent())

    val plan = plan(findClass("Outer").findFieldByName("count", false)!!, "total") as ModPlan.AcceptedPlan
    val excludedPlaces = placesAt(plan, outer, outer.text.indexOf("count; }"))
    val ready = inBackground { planWithout(excludedPlaces) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertSize(1, excludedPlaces)
    assertEquals("""
      public class Outer {
        int total;
        class Inner {
          int total;
          int get() { return count; }
          int twice() { return Outer.this.total * 2; }
        }
      }
      """.trimIndent(), outer.text)
  }

  /** As in the legacy rename: the static import keeps the overload that keeps its name, and the call of the renamed one is qualified. */
  fun testStaticImportOfTwoOverloadsStaysWhenOneIsRenamed() {
    myFixture.addFileToProject("p/A.java", """
      package p;

      public class A {
        public static void m(int i) {}
        public static void m(String s) {}
      }
      """.trimIndent())
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import static p.A.m;

      class User {
        void f() {
          m(1);
          m("s");
        }
      }
      """.trimIndent())
    val intOverload = findClass("p.A").findMethodsByName("m", false).first { it.parameterList.parameters[0].type.canonicalText == "int" }

    rename(intOverload, "n")

    assertEquals("""
      package q;

      import p.A;

      import static p.A.m;

      class User {
        void f() {
          A.n(1);
          m("s");
        }
      }
      """.trimIndent(), user.text)
  }

  /**
   * A call through a static import that a method of the new name takes is a collision and a reference at one place. The exclusion of the
   * place removes both, as in the legacy preview: the call keeps its text, and the other call is qualified.
   */
  fun testExcludedPlaceOfACollisionWithAStaticImportKeepsItsText() {
    myFixture.addFileToProject("p/A.java", "package p;\n\npublic class A {\n  public static void foo() {}\n}")
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import static p.A.foo;

      class User {
        void bar() {}
        void f() {
          foo();
          foo();
        }
      }
      """.trimIndent())

    val plan = plan(findClass("p.A").findMethodsByName("foo", false).single(), "bar") as ModPlan.AcceptedPlan
    val excludedPlaces = placesAt(plan, user, user.text.indexOf("foo();"))
    val ready = inBackground { planWithout(excludedPlaces) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertSize(1, excludedPlaces)
    assertEquals("""
      package q;

      import p.A;

      class User {
        void bar() {}
        void f() {
          foo();
          A.bar();
        }
      }
      """.trimIndent(), user.text)
  }

  /** As the legacy preview: the excluded single import of the renamed class stays, and the file gets an import of the new name. */
  fun testExcludedSingleImportOfTheRenamedClass() {
    myFixture.addFileToProject("p/Old.java", "package p;\n\npublic class Old {}")
    val user = myFixture.addFileToProject("q/User.java", """
      package q;

      import p.Old;

      public class User {
        Old o = new Old();
      }
      """.trimIndent())

    val plan = plan(findClass("p.Old"), "New") as ModPlan.AcceptedPlan
    val excludedPlaces = placesAt(plan, user, user.text.indexOf("Old;"))
    val ready = inBackground { planWithout(excludedPlaces) } as ModPlan.AcceptedPlan
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    assertSize(1, excludedPlaces)
    assertEquals("""
      package q;

      import p.New;
      import p.Old;

      public class User {
        New o = new New();
      }
      """.trimIndent(), user.text)
  }

  /** One comment names the class and a variable that the variable renamer renames: both subjects write the comment. */
  fun testCommentThatNamesTwoRenamedElementsGetsBothNewNames() {
    myFixture.addFileToProject("Foo.java", "public class Foo {}")
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        void f() {
          // Foo foo
          Foo foo = new Foo();
        }
      }
      """.trimIndent())

    rename(findClass("Foo"), "Bar", relatedOn().with(variablesSwitch(), true).with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, true))

    assertEquals("""
      public class User {
        void f() {
          // Bar bar
          Bar bar = new Bar();
        }
      }
      """.trimIndent(), user.text)
  }

  /**
   * The reference to the field holds the reference to the class. The class is written first and gets a longer name, so the reference to the
   * field moves.
   */
  fun testReferenceThatAnEarlierSubjectMakesLongerGetsItsNewName() {
    myFixture.addFileToProject("Foo.java", """
      public class Foo {
        static Foo foo;
      }
      """.trimIndent())
    val user = myFixture.addFileToProject("User.java", """
      public class User {
        Object o = Foo.foo;
      }
      """.trimIndent())

    rename(findClass("Foo"), "FooBar", relatedOn().with(variablesSwitch(), true))

    assertEquals("""
      public class User {
        Object o = FooBar.fooBar;
      }
      """.trimIndent(), user.text)
  }

  fun testFieldAndItsUsageAreRenamed() {
    myFixture.addFileToProject("Counter.java", """
      public class Counter {
        int hits;
      }
      """.trimIndent())
    val reader = myFixture.addFileToProject("Reader.java", """
      public class Reader {
        int read(Counter counter) {
          return counter.hits;
        }
      }
      """.trimIndent())

    rename(findClass("Counter").findFieldByName("hits", false)!!, "count")

    assertNotNull(findClass("Counter").findFieldByName("count", false))
    assertNull(findClass("Counter").findFieldByName("hits", false))
    assertEquals("""
      public class Reader {
        int read(Counter counter) {
          return counter.count;
        }
      }
      """.trimIndent(), reader.text)
  }

  fun testAnalyzeWritesNothing() {
    val plain = myFixture.addFileToProject("Plain.java", """
      public class Plain {
        int hits;
      }
      """.trimIndent())
    val reader = myFixture.addFileToProject("PlainReader.java", """
      public class PlainReader {
        int read(Plain plain) {
          return plain.hits;
        }
      }
      """.trimIndent())
    val plainText = plain.text
    val readerText = reader.text

    val ready = assertInstanceOf(plan(findClass("Plain").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)

    assertContainsElements(ready.execution.affectedFiles, plain.virtualFile, reader.virtualFile)
    assertEquals(plainText, plain.text)
    assertEquals(readerText, reader.text)
    assertNotNull(findClass("Plain").findFieldByName("hits", false))
  }

  fun testUsageInAReadOnlyFileIsRefused() {
    myFixture.addFileToProject("Signal.java", """
      public class Signal {
        int hits;
      }
      """.trimIndent())
    val locked = myFixture.addFileToProject("Locked.java", """
      public class Locked {
        int read(Signal signal) {
          return signal.hits;
        }
      }
      """.trimIndent())
    val lockedFile = locked.virtualFile
    val lockedText = locked.text
    val ready = assertInstanceOf(plan(findClass("Signal").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)

    WriteAction.runAndWait<Exception> { lockedFile.isWritable = false }
    try {
      val result = assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.ReadOnly::class.java)

      assertContainsElements(result.files, lockedFile)
    }
    finally {
      WriteAction.runAndWait<Exception> { lockedFile.isWritable = true }
    }
    assertNotNull(findClass("Signal").findFieldByName("hits", false))
    assertEquals(lockedText, locked.text)
  }

  fun testAReadOnlyDeclarationIsRefused() {
    val sealed = myFixture.addFileToProject("Sealed.java", """
      public class Sealed {
        int hits;
      }
      """.trimIndent())
    val sealedFile = sealed.virtualFile
    val sealedText = sealed.text
    val ready = assertInstanceOf(plan(findClass("Sealed").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)

    WriteAction.runAndWait<Exception> { sealedFile.isWritable = false }
    try {
      val result = assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.ReadOnly::class.java)

      assertContainsElements(result.files, sealedFile)
    }
    finally {
      WriteAction.runAndWait<Exception> { sealedFile.isWritable = true }
    }
    assertNotNull(findClass("Sealed").findFieldByName("hits", false))
    assertEquals(sealedText, sealed.text)
  }

  fun testAReadOnlyUsageIsSkippedWithTheOption() {
    myFixture.addFileToProject("Meter.java", """
      public class Meter {
        int hits;
      }
      """.trimIndent())
    val viewer = myFixture.addFileToProject("Viewer.java", """
      public class Viewer {
        int read(Meter meter) {
          return meter.hits;
        }
      }
      """.trimIndent())
    val viewerFile = viewer.virtualFile
    val viewerText = viewer.text

    WriteAction.runAndWait<Exception> { viewerFile.isWritable = false }
    try {
      val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SKIP_READ_ONLY_USAGES, true)
      val ready = assertInstanceOf(plan(findClass("Meter").findFieldByName("hits", false)!!, "count", options), ModPlan.AcceptedPlan::class.java)

      assertFalse(ready.usages.any { it.virtualFile == viewerFile })
      assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)
    }
    finally {
      WriteAction.runAndWait<Exception> { viewerFile.isWritable = true }
    }
    assertNotNull(findClass("Meter").findFieldByName("count", false))
    assertEquals(viewerText, viewer.text)
  }

  fun testAReadOnlyUsageRefusesTheWriteWithoutTheOption() {
    myFixture.addFileToProject("Meter.java", """
      public class Meter {
        int hits;
      }
      """.trimIndent())
    val viewer = myFixture.addFileToProject("Viewer.java", """
      public class Viewer {
        int read(Meter meter) {
          return meter.hits;
        }
      }
      """.trimIndent())
    val viewerFile = viewer.virtualFile

    WriteAction.runAndWait<Exception> { viewerFile.isWritable = false }
    try {
      val ready = assertInstanceOf(plan(findClass("Meter").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)

      assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.ReadOnly::class.java)
    }
    finally {
      WriteAction.runAndWait<Exception> { viewerFile.isWritable = true }
    }
    assertNotNull(findClass("Meter").findFieldByName("hits", false))
  }

  fun testAReadOnlyDeclarationRefusesTheWriteWithTheOption() {
    val meter = myFixture.addFileToProject("Meter.java", """
      public class Meter {
        int hits;
      }
      """.trimIndent())
    val meterFile = meter.virtualFile

    WriteAction.runAndWait<Exception> { meterFile.isWritable = false }
    try {
      val options = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.SKIP_READ_ONLY_USAGES, true)
      val ready = assertInstanceOf(plan(findClass("Meter").findFieldByName("hits", false)!!, "count", options), ModPlan.AcceptedPlan::class.java)

      assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.ReadOnly::class.java)
    }
    finally {
      WriteAction.runAndWait<Exception> { meterFile.isWritable = true }
    }
    assertNotNull(findClass("Meter").findFieldByName("hits", false))
  }

  fun testACollisionOfARelatedRenameLeavesTheVariableOut() {
    myFixture.addFileToProject("Gear.java", """
      public class Gear {
      }
      """.trimIndent())
    val box = myFixture.addFileToProject("Box.java", """
      public class Box {
        void use() {
          Gear gear = null;
          int cog = 0;
        }
      }
      """.trimIndent())

    rename(findClass("Gear"), "Cog", relatedOn().with(variablesSwitch(), true))

    assertEquals("""
      public class Box {
        void use() {
          Cog gear = null;
          int cog = 0;
        }
      }
      """.trimIndent(), box.text)
  }

  fun testAnInvalidNewNameIsRefused() {
    val gauge = myFixture.addFileToProject("Gauge.java", """
      public class Gauge {
        int hits;
      }
      """.trimIndent())
    val gaugeText = gauge.text

    val refused = assertInstanceOf(plan(findClass("Gauge").findFieldByName("hits", false)!!, "not a name"), ModPlan.RefusedPlan::class.java)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refused.reason)
    assertEquals(gaugeText, gauge.text)
    assertNotNull(findClass("Gauge").findFieldByName("hits", false))
  }

  fun testARenameOfTheCaseRenamesTheFile() {
    myFixture.addFileToProject("gauge.java", """
      public class gauge {
      }
      """.trimIndent())

    rename(findClass("gauge"), "Gauge")

    assertEquals("Gauge.java", findClass("Gauge").containingFile.name)
  }

  fun testPlanRunsOnlyOnce() {
    myFixture.addFileToProject("Once.java", """
      public class Once {
        int hits;
      }
      """.trimIndent())
    val ready = assertInstanceOf(plan(findClass("Once").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)

    val second = ModPlanExecutor.execute(ready.execution, true)

    assertInstanceOf(second, ModPlanExecutionResult.Stale::class.java)
    assertNotNull(findClass("Once").findFieldByName("count", false))
  }

  fun testAnUncommittedChangeStopsThePlan() {
    val pulse = myFixture.addFileToProject("Pulse.java", """
      public class Pulse {
        int hits;
      }
      """.trimIndent())
    val ready = assertInstanceOf(plan(findClass("Pulse").findFieldByName("hits", false)!!, "count"), ModPlan.AcceptedPlan::class.java)
    val document = FileDocumentManager.getInstance().getDocument(pulse.virtualFile)!!

    WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "// a note\n") }
    assertTrue("the fixture committed the change, so this test proves nothing",
               com.intellij.psi.PsiDocumentManager.getInstance(project).isUncommited(document))

    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Stale::class.java)
    assertTrue(document.text.contains("int hits;"))
    assertNotNull(findClass("Pulse").findFieldByName("hits", false))
  }

  /** @return the places of [plan] in [file] that hold [offset] */
  private fun placesAt(plan: ModPlan.AcceptedPlan, file: PsiFile, offset: Int): List<SmartPsiFileRange> =
    plan.usages.filter { place ->
      val range = place.range
      place.virtualFile == file.virtualFile && range != null && range.startOffset <= offset && offset < range.endOffset
    }

  private fun <T> inBackground(action: () -> T): T {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable { runReadActionBlocking(action) }))
  }

  /**
   * Runs the chain of [request] without a user: each option of a form is answered with [isOn], and an option that the request answers is
   * not asked. The run stops at the first custom question, which needs a dialog.
   *
   * @return every question that the chain asks, in its order, up to the first custom one
   */
  private fun askedQuestions(request: ModPlanRequest, isOn: (ModPlanOption<*>) -> Boolean = { false }): List<ModPlanQuestion> {
    val asked = ArrayList<ModPlanQuestion>()
    val chain = ModPlanRenameRefactoring.getInstance()::chain
    inBackground {
      var result = ModPlanChain.runToQuestions(request, chain)
      while (result is ModPlanChain.ChainResult.Asked<ModPlanFinalStep<ModPlan>>) {
        val question = result.question
        asked.add(question)
        if (question !is ModPlanQuestion.Form) break
        val answers = question.options().fold(result.answers) { values, option -> withAnswer(values, option, isOn(option)) }
        result = ModPlanChain.runAfterAnswers(request, chain, result, answers)
      }
    }
    return asked
  }

  @Suppress("UNCHECKED_CAST")
  private fun withAnswer(values: ModPlanOptionValues, option: ModPlanOption<*>, isOn: Boolean): ModPlanOptionValues =
    values.with(option as ModPlanOption<Boolean>, isOn)

  private fun plan(element: PsiElement, newName: String, options: ModPlanOptionValues = ModPlanOptionValues.EMPTY): ModPlan = inBackground {
    val request = ModPlanRenameRefactoring.request(project, element, newName, substitute = true, options)
    lastRequest = request
    ModPlanRenameRefactoring.getInstance().plan(request)
  }

  /** The request of the last [plan]. */
  private var lastRequest: ModPlanRequest? = null

  /** @return the plan of the request of the last [plan] without [excludedUsages], as the flow plans after a preview; call it in [inBackground] */
  private fun planWithout(excludedUsages: List<SmartPsiFileRange>): ModPlan {
    val request = lastRequest!!
    val excluded = runReadActionBlocking { ModPlanRefactoring.excludedUsagesOf(excludedUsages) }
    return ModPlanRenameRefactoring.getInstance()
      .plan(ModPlanChain.withOptionValues(request, request.optionValues().with(ModPlanRefactoring.EXCLUDED_USAGES, excluded)))
  }

  private fun rename(element: PsiElement, newName: String, options: ModPlanOptionValues = ModPlanOptionValues.EMPTY) {
    val ready = when (val plan = plan(element, newName, options)) {
      is ModPlan.RefusedPlan -> throw AssertionError("refused: ${plan.reason} ${plan.message}")
      is ModPlan.AcceptedPlan -> plan
    }
    val messages = ready.conflicts.conflicts.map { it.messages }
    assertFalse("unexpected conflicts: $messages", ready.conflicts.needsConfirmation())
    assertInstanceOf(ModPlanExecutor.execute(ready.execution, true), ModPlanExecutionResult.Applied::class.java)
  }

  private fun relatedOn(): ModPlanOptionValues = ModPlanOptionValues.EMPTY.with(ModPlanRenameOptions.RENAME_RELATED, true)

  private fun variablesSwitch(): ModPlanOption<Boolean> =
    ModPlanRenameOptions.optionForRenamer(AutomaticRenamerFactory.EP_NAME.findExtensionOrFail(AutomaticVariableRenamerFactory::class.java))!!

  private fun runMethod(className: String) = findClass(className).findMethodsByName("run", false).single()

  private fun findClass(name: String): PsiClass =
    JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.projectScope(project))!!

  private fun findPackage(name: String): PsiPackage = JavaPsiFacade.getInstance(project).findPackage(name)!!

  private companion object {
    const val SCOPE_TEXT = """
      public class Main {
        void m() {}
        void f() {
          m();
          m();
          m();
        }
      }
      """
  }
}
