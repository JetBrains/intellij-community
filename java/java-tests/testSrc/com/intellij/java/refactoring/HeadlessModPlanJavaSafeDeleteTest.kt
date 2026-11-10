// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.ExternalAnnotationsManagerImpl
import com.intellij.codeInsight.intention.QuickFixFactory
import com.intellij.codeInspection.options.OptionContainer
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModDisplayMessage
import com.intellij.modcommand.ModEditOptions
import com.intellij.modcommand.ModShowConflicts
import com.intellij.modcommand.Presentation
import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.impl.light.LightDefaultConstructor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanCommandAction
import com.intellij.refactoring.modplan.ModPlanCommandInterpreter
import com.intellij.refactoring.modplan.ModPlanExecutionResult
import com.intellij.refactoring.modplan.ModPlanExecutor
import com.intellij.refactoring.modplan.ModPlanOption
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanQuestion
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverrideAnnotation
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceSimpleDeleteUsageInfo
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.ModPlanTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import com.intellij.usageView.UsageInfo
import java.util.concurrent.Callable

/**
 * The analysis runs on a background thread, as the MCP tool runs it: a question to the user or a modal progress fails the test.
 */
class HeadlessModPlanJavaSafeDeleteTest : LightJavaCodeInsightFixtureTestCase() {

  fun testUnusedMethodIsDeleted() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void unused() {}
        public void kept() {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "unused"))

    assertEmpty(findClass("Worker").findMethodsByName("unused", false))
    assertSize(1, findClass("Worker").findMethodsByName("kept", false))
  }

  fun testUsedMethodIsRefusedWithConflict() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void used() {}
        public void caller() { used(); }
      }
      """.trimIndent())

    val readyPlan = ready(method("Worker", "used"))

    assertTrue("a usage must be a conflict", readyPlan.conflicts.hasBroken())
    assertSize(1, findClass("Worker").findMethodsByName("used", false))
  }

  fun testAccessorsAreDeletedWithTheFieldByDefault() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
      }
      """.trimIndent())

    safeDelete(findClass("Bean").findFieldByName("name", false)!!)

    assertEmpty(findClass("Bean").fields)
    assertEmpty(findClass("Bean").methods)
  }

  fun testAccessorsAreKeptWhenTheOptionSaysSo() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String name;
        public String getName() { return name; }
      }
      """.trimIndent())

    val readyPlan = ready(findClass("Bean").findFieldByName("name", false)!!, ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS, false))

    assertTrue("the getter reads the field, so the deletion must be refused", readyPlan.conflicts.hasBroken())
  }

  fun testOverridersAreDeletedByDefault() {
    addBaseWithOverride()

    safeDelete(method("Base", "run"))

    assertEmpty(findClass("Base").findMethodsByName("run", false))
    assertEmpty(findClass("Impl").findMethodsByName("run", false))
  }

  fun testOverridersAreKeptWhenTheOptionSaysSo() {
    addBaseWithOverride()

    safeDelete(method("Base", "run"), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false))

    assertEmpty(findClass("Base").findMethodsByName("run", false))
    val keptMethod = method("Impl", "run")
    assertNull(keptMethod.modifierList.findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE))
    assertTrue("nothing outside Impl calls it", keptMethod.hasModifierProperty(PsiModifier.PRIVATE))
  }

  fun testUnusedCalleesAreKeptByDefault() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { helper(); }
        private void helper() {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "caller"))

    assertSize(1, findClass("Worker").findMethodsByName("helper", false))
  }

  fun testUnusedCalleesAreDeletedWhenTheOptionSaysSo() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { helper(); }
        private void helper() {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "caller"), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true))

    assertEmpty(findClass("Worker").findMethodsByName("helper", false))
  }

  fun testFieldWithAccessorsAsksToDeleteThem() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String name;
        public String getName() { return name; }
      }
      """.trimIndent())

    val askedQuestions = questionsOf(findClass("Bean").findFieldByName("name", false)!!).filterIsInstance<ModPlanQuestion.Custom>()

    assertSize(1, askedQuestions.filter { ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS in it.options() })
  }

  fun testMethodWithOverridersAsksWhichToDelete() {
    addBaseWithOverride()

    val customQuestion = questionsOf(method("Base", "run")).filterIsInstance<ModPlanQuestion.Custom>().single()

    assertEquals(listOf(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE), customQuestion.options())
  }

  fun testMethodWithUnusedCalleesAsksWhichToDelete() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { helper(); }
        private void helper() {}
      }
      """.trimIndent())

    val customQuestion = questionsOf(method("Worker", "caller")).filterIsInstance<ModPlanQuestion.Custom>().single()

    assertEquals(listOf(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE), customQuestion.options())
  }

  fun testOnlyTheChosenOverriderIsDeleted() {
    myFixture.addFileToProject("Base.java", "public class Base { public void run() {} }")
    myFixture.addFileToProject("First.java", "public class First extends Base { public void run() {} }")
    myFixture.addFileToProject("Second.java", "public class Second extends Base { public void run() {} }")
    val chosenName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("First", "run")) }!!

    safeDelete(method("Base", "run"), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, chosenName))

    assertEmpty(findClass("Base").findMethodsByName("run", false))
    assertEmpty(findClass("First").findMethodsByName("run", false))
    assertSize(1, findClass("Second").findMethodsByName("run", false))
  }

  fun testOnlyTheChosenCalleeIsDeleted() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { first(); second(); }
        private void first() {}
        private void second() {}
      }
      """.trimIndent())
    val chosenName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("Worker", "first")) }!!
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true).with(ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE, chosenName)

    safeDelete(method("Worker", "caller"), optionValues)

    assertEmpty(findClass("Worker").findMethodsByName("first", false))
    assertSize(1, findClass("Worker").findMethodsByName("second", false))
  }

  fun testNestedCalleeIsDeletedWhenTheChooserChecksIt() {
    addCallerWithTwoLevelsOfCallees()
    val chosenNames = runReadActionBlocking { listOf(method("Worker", "helper1"), method("Worker", "helper2")).joinToString("\n") { PsiFormatUtil.getExternalName(it)!! } }
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true).with(ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE, chosenNames)

    safeDelete(method("Worker", "caller"), optionValues)

    assertEmpty(findClass("Worker").findMethodsByName("helper1", false))
    assertEmpty(findClass("Worker").findMethodsByName("helper2", false))
  }

  fun testNestedCalleeOfAnUncheckedCalleeIsKept() {
    addCallerWithTwoLevelsOfCallees()
    val chosenName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("Worker", "helper2")) }!!
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true).with(ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE, chosenName)

    safeDelete(method("Worker", "caller"), optionValues)

    assertSize(1, findClass("Worker").findMethodsByName("helper1", false))
    assertSize(1, findClass("Worker").findMethodsByName("helper2", false))
  }

  private fun addCallerWithTwoLevelsOfCallees() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { helper1(); }
        private void helper1() { helper2(); }
        private void helper2() {}
      }
      """.trimIndent())
  }

  /** As the legacy optimize imports helper, the imports that the deletion leaves unused go too. */
  fun testImportOfTheDeletedParameterTypeIsRemoved() {
    myFixture.addFileToProject("p/Bar.java", "package p; public class Bar {}")
    val worker = myFixture.addFileToProject("Worker.java", """
      import p.Bar;

      public class Worker {
        void run(Bar bar) {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "run").parameterList.parameters[0])

    assertFalse(worker.text, worker.text.contains("import p.Bar;"))
  }

  fun testCallersKeepTheParameterThatTheyPassOnByDefault() {
    addCallersThatPassOnTheParameter()

    safeDelete(method("Worker", "bar").parameterList.parameters[0])

    assertEquals(0, method("Worker", "bar").parameterList.parametersCount)
    assertEquals(1, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(1, method("Worker", "baz").parameterList.parametersCount)
  }

  /** As the legacy chooser, the parameter of a caller goes with its arguments in the calls of the caller. */
  fun testParameterIsDeletedInTheCallersWhenTheOptionSaysSo() {
    addCallersThatPassOnTheParameter()

    safeDelete(method("Worker", "bar").parameterList.parameters[0],
               ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true))

    assertEquals(0, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(0, method("Worker", "baz").parameterList.parametersCount)
    val text = findClass("Worker").containingFile.text
    assertTrue(text, text.contains("void main() { foo(); baz(); }"))
  }

  fun testOnlyTheChosenCallerLosesTheParameter() {
    addCallersThatPassOnTheParameter()
    val chosenName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("Worker", "foo")) }!!
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)
      .with(ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN, chosenName)

    safeDelete(method("Worker", "bar").parameterList.parameters[0], optionValues)

    assertEquals(0, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(1, method("Worker", "baz").parameterList.parametersCount)
  }

  fun testParameterThatCallersPassOnAsksForTheCallers() {
    addCallersThatPassOnTheParameter()

    val customQuestions = questionsOf(method("Worker", "bar").parameterList.parameters[0]).filterIsInstance<ModPlanQuestion.Custom>()

    assertEquals(listOf(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN),
                 customQuestions.single().options())
  }

  /** The fix of a parameter keeps the parameter in the callers that pass it on in batch execution, as the chooser starts unchecked. */
  fun testFixOfAParameterKeepsItInTheCallersInBatchExecution() {
    addCallersThatPassOnTheParameter()
    TestModeFlags.set(ModPlanCommandInterpreter.ASKS_IN_TESTS, true, testRootDisposable)
    val file = findClass("Worker").containingFile

    executeInBatch(file, parameterFixCommand(file))

    assertEquals(0, method("Worker", "bar").parameterList.parametersCount)
    assertEquals(1, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(1, method("Worker", "baz").parameterList.parametersCount)
  }

  /** The fix of a parameter asks about the callers that pass it on, and the answer deletes the parameter in them. */
  fun testFixOfAParameterAsksAboutTheCallers() {
    addCallersThatPassOnTheParameter()
    TestModeFlags.set(ModPlanCommandInterpreter.ASKS_IN_TESTS, true, testRootDisposable)
    val file = findClass("Worker").containingFile
    val callersCommand = assertInstanceOf(parameterFixCommand(file), ModEditOptions::class.java)
    val container = callersCommand.containerSupplier().get() as OptionContainer
    val question = container.optionController.getOption(ModPlanQuestion.Custom.QUESTION_BIND_ID) as ModPlanQuestion.Custom
    val answers = container.optionController.getOption(ModPlanQuestion.Custom.ANSWERS_BIND_ID) as ModPlanOptionValues
    val deleteInCallers = answers.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)

    val nextCommand = inBackground { callersCommand.applyOptions(mapOf(ModPlanQuestion.Custom.ANSWERS_BIND_ID to deleteInCallers)) }
    executeInBatch(file, nextCommand)

    assertTrue(ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN in question.options())
    assertEquals(0, method("Worker", "bar").parameterList.parametersCount)
    assertEquals(0, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(0, method("Worker", "baz").parameterList.parametersCount)
  }

  /** Runs [command] as batch execution does, such as Code Cleanup: in a command, with the defaults of its questions. */
  private fun executeInBatch(file: PsiFile, command: ModCommand) {
    CommandProcessor.getInstance().executeCommand(project, {
      ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, file), command)
    }, null, null)
  }

  /** @return the command of the safe delete fix for the parameter of `Worker.bar` */
  private fun parameterFixCommand(file: PsiFile): ModCommand = inBackground {
    QuickFixFactory.getInstance().createSafeDeleteFix(method("Worker", "bar").parameterList.parameters[0])
      .asModCommandAction()!!.perform(ActionContext.from(null, file))
  }

  private fun addCallersThatPassOnTheParameter() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        void foo(int i) { bar(i); }
        void baz(int i) { bar(i); }
        void bar(int i) {}
        void main() { foo(1); baz(2); }
      }
      """.trimIndent())
  }

  /** As the legacy chooser, a chosen caller of a chosen caller loses the parameter too. */
  fun testChosenCallerOfAChosenCallerLosesTheParameter() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        void first(int i) {}
        void second(int i) { first(i); }
        void third(int i) { second(i); }
        void main() { third(1); }
      }
      """.trimIndent())
    val chosenNames = runReadActionBlocking { listOf(method("Worker", "second"), method("Worker", "third")).joinToString("\n") { PsiFormatUtil.getExternalName(it)!! } }
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)
      .with(ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN, chosenNames)

    safeDelete(method("Worker", "first").parameterList.parameters[0], optionValues)

    assertEquals(0, method("Worker", "second").parameterList.parametersCount)
    assertEquals(0, method("Worker", "third").parameterList.parametersCount)
    val text = findClass("Worker").containingFile.text
    assertTrue(text, text.contains("void main() { third(); }"))
  }

  fun testParamTagOfTheCallerGoesWithItsParameter() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        /** @param i the value that goes on */
        void foo(int i) { bar(i); }
        void bar(int i) {}
        void main() { foo(1); }
      }
      """.trimIndent())

    safeDelete(method("Worker", "bar").parameterList.parameters[0],
               ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true))

    assertEquals(0, method("Worker", "foo").parameterList.parametersCount)
    val text = findClass("Worker").containingFile.text
    assertFalse(text, text.contains("@param i"))
  }

  fun testParamTagGoesWithTheParameter() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        /**
         * @param unused the deleted value
         * @param kept the kept value
         */
        void run(int unused, int kept) {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "run").parameterList.parameters[0])

    val text = findClass("Worker").containingFile.text
    assertFalse(text, text.contains("@param unused"))
    assertTrue(text, text.contains("@param kept"))
  }

  fun testVarargArgumentsGoWithTheParameter() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        void run(int first, String... rest) {}
        void caller() { run(1, "a", "b"); }
      }
      """.trimIndent())

    safeDelete(method("Worker", "run").parameterList.parameters[1])

    val text = findClass("Worker").containingFile.text
    assertTrue(text, text.contains("void caller() { run(1); }"))
  }

  fun testFieldWithAnUnusedCalleeAsksWhichToDelete() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        private int value = helper();
        private static int helper() { return 1; }
      }
      """.trimIndent())

    val customQuestion = questionsOf(findClass("Worker").findFieldByName("value", false)!!).filterIsInstance<ModPlanQuestion.Custom>().single()

    assertEquals(listOf(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE), customQuestion.options())
  }

  fun testAnswersOfTwoSuperMethodQuestionsAddUp() {
    addTwoMethodsWithBases()
    fun questionOf(method: PsiMethod) = questionsOf(method).filterIsInstance<ModPlanQuestion.Custom>()
      .single { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) }

    val firstAnswers = questionOf(method("Impl1", "a")).ask(project, ModPlanOptionValues.EMPTY)!!
    val bothAnswers = questionOf(method("Impl2", "b")).ask(project, firstAnswers)!!

    val expectedNames = runReadActionBlocking { setOf(PsiFormatUtil.getExternalName(method("Impl1", "a")), PsiFormatUtil.getExternalName(method("Impl2", "b"))) }
    assertEquals(expectedNames, bothAnswers.chosenKeys(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    assertEquals(expectedNames, bothAnswers.answeredKeys(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    assertFalse("the dialog leaves the general answer to the request", bothAnswers.has(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS))
  }

  fun testBaseMethodIsDeletedOnlyForTheMethodsThatAreNamed() {
    addTwoMethodsWithBases()
    val namedMethodName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("Impl1", "a")) }!!
    val optionValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true).with(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF, namedMethodName)

    safeDelete(listOf(method("Impl1", "a"), method("Impl2", "b")), optionValues)

    assertEmpty(findClass("Impl1").findMethodsByName("a", false))
    assertEmpty(findClass("Base1").findMethodsByName("a", false))
    assertEmpty(findClass("Impl2").findMethodsByName("b", false))
    assertSize(1, findClass("Base2").findMethodsByName("b", false))
  }

  fun testOnlyTheOverridersThatAreNamedAreDeletedForSeveralMethods() {
    addTwoMethodsWithBases()
    val namedMethodName = runReadActionBlocking { PsiFormatUtil.getExternalName(method("Impl1", "a")) }!!

    safeDelete(listOf(method("Base1", "a"), method("Base2", "b")), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, namedMethodName))

    assertEmpty(findClass("Base1").findMethodsByName("a", false))
    assertEmpty(findClass("Impl1").findMethodsByName("a", false))
    assertEmpty(findClass("Base2").findMethodsByName("b", false))
    assertSize(1, findClass("Impl2").findMethodsByName("b", false))
  }

  private fun addTwoMethodsWithBases() {
    myFixture.addFileToProject("Base1.java", "public class Base1 { public void a() {} }")
    myFixture.addFileToProject("Impl1.java", "public class Impl1 extends Base1 { @Override public void a() {} }")
    myFixture.addFileToProject("Base2.java", "public class Base2 { public void b() {} }")
    myFixture.addFileToProject("Impl2.java", "public class Impl2 extends Base2 { @Override public void b() {} }")
  }

  fun testBaseMethodDeletedWithTheOverrideIsNotAskedAbout() {
    addBaseWithOverride()
    val targets = listOf(method("Impl", "run"), method("Base", "run"))

    val askedQuestions = questionsOf(targets).filterIsInstance<ModPlanQuestion.Custom>().filter { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) }
    safeDelete(targets)

    assertEmpty(askedQuestions)
    assertEmpty(findClass("Impl").findMethodsByName("run", false))
    assertEmpty(findClass("Base").findMethodsByName("run", false))
  }

  fun testBaseMethodDeletedWithTheOverrideGivesTheSameDeletionWithTheOption() {
    addBaseWithOverride()

    safeDelete(listOf(method("Impl", "run"), method("Base", "run")), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true))

    assertEmpty(findClass("Impl").findMethodsByName("run", false))
    assertEmpty(findClass("Base").findMethodsByName("run", false))
  }

  private fun addBaseWithOverride() {
    myFixture.addFileToProject("Base.java", "public class Base { public void run() {} }")
    myFixture.addFileToProject("Impl.java", "public class Impl extends Base { @Override public void run() {} }")
  }

  /** An earlier usage may remove the method: the removal of its @Override then writes nothing, as before. */
  fun testOverrideAnnotationOfAGoneMethodWritesNothing() {
    addBaseWithOverride()
    val usage = runReadActionBlocking { SafeDeleteOverrideAnnotation(method("Impl", "run"), method("Base", "run")) }

    WriteCommandAction.runWriteCommandAction(project) {
      method("Impl", "run").delete()
      usage.performRefactoring()
    }
  }

  fun testParameterInAHierarchyAsksToDeleteTheHierarchy() {
    myFixture.addFileToProject("Base.java", "public class Base { public void run(int unused) {} }")
    myFixture.addFileToProject("Impl.java", "public class Impl extends Base { @Override public void run(int unused) {} }")

    val askedQuestions = questionsOf(method("Base", "run").parameterList.parameters[0]).filterIsInstance<ModPlanQuestion.Custom>()

    assertSize(1, askedQuestions.filter { ModPlanSafeDeleteOptions.DELETE_HIERARCHY in it.options() })
  }

  fun testOverridingMethodDoesNotAskTheHierarchyQuestion() {
    addBaseWithOverride()

    val askedOptions = questionsOf(method("Impl", "run")).filterIsInstance<ModPlanQuestion.Form>().flatMap { it.options() }

    assertFalse(ModPlanSafeDeleteOptions.DELETE_HIERARCHY in askedOptions)
  }

  fun testLocalVariableAsksOnlyThePlatformQuestions() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void run() { int unused = 1; } }")
    val variable = runReadActionBlocking { PsiTreeUtil.findChildOfType(method("Worker", "run"), PsiLocalVariable::class.java)!! }

    assertEquals(
      listOf(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE),
      questionsOf(variable).flatMap { (it as ModPlanQuestion.Form).options() },
    )
  }

  fun testFileOfTheLastClassIsPredictedDeleted() {
    val file = myFixture.addFileToProject("Single.java", "public class Single {}")
    myFixture.addFileToProject("Pair.java", """
      public class Pair {}
      class Other {}
      """.trimIndent())

    assertEquals(listOf(file.virtualFile), deletedFiles(ready(findClass("Single"))))
    assertEmpty(deletedFiles(ready(findClass("Pair"))))
  }

  fun testJavaFileIsDeleted() {
    val file = myFixture.addFileToProject("Single.java", "public class Single {}")
    val virtualFile = file.virtualFile

    safeDelete(file)

    assertFalse(virtualFile.isValid)
  }

  fun testParameterIsDeletedInTheWholeHierarchyByDefault() {
    myFixture.addFileToProject("Base.java", """
      public class Base {
        public void run(int unused) {}
      }
      """.trimIndent())
    myFixture.addFileToProject("Impl.java", """
      public class Impl extends Base {
        @Override
        public void run(int unused) {}
      }
      """.trimIndent())

    safeDelete(method("Base", "run").parameterList.parameters[0])

    assertEquals(0, method("Base", "run").parameterList.parametersCount)
    assertEquals(0, method("Impl", "run").parameterList.parametersCount)
  }

  fun testParameterIsDeletedOnlyInTheSelectedMethodWhenTheOptionSaysSo() {
    myFixture.addFileToProject("Base.java", """
      public class Base {
        public void run(int unused) {}
      }
      """.trimIndent())
    myFixture.addFileToProject("Impl.java", """
      public class Impl extends Base {
        public void run(int unused) {}
      }
      """.trimIndent())

    safeDelete(method("Base", "run").parameterList.parameters[0], ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.DELETE_HIERARCHY, false))

    assertEquals(0, method("Base", "run").parameterList.parametersCount)
    assertEquals(1, method("Impl", "run").parameterList.parametersCount)
  }

  fun testTypeParameterIsDeletedInTheWholeHierarchyByDefault() {
    myFixture.addFileToProject("Base.java", """
      public class Base {
        public <T> void run() {}
      }
      """.trimIndent())
    myFixture.addFileToProject("Impl.java", """
      public class Impl extends Base {
        @Override
        public <T> void run() {}
      }
      """.trimIndent())

    safeDelete(method("Base", "run").typeParameters[0])

    assertEmpty(method("Base", "run").typeParameters)
    assertEmpty(method("Impl", "run").typeParameters)
  }

  fun testTypeParameterIsDeletedOnlyInTheSelectedMethodWhenTheOptionSaysSo() {
    myFixture.addFileToProject("Base.java", """
      public class Base {
        public <T> void run() {}
      }
      """.trimIndent())
    myFixture.addFileToProject("Impl.java", """
      public class Impl extends Base {
        public <T> void run() {}
      }
      """.trimIndent())

    safeDelete(method("Base", "run").typeParameters[0], ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.DELETE_HIERARCHY, false))

    assertEmpty(method("Base", "run").typeParameters)
    assertSize(1, method("Impl", "run").typeParameters)
  }

  fun testRecordComponentIsDeleted() {
    myFixture.addFileToProject("Point.java", "public record Point(int x, int unused) {}")

    safeDelete(findClass("Point").recordComponents[1])

    assertEquals(listOf("x"), findClass("Point").recordComponents.map { it.name })
  }

  fun testSuperclassWithOnlyPrivatesIsRemovedFromTheExtendsList() {
    myFixture.addFileToProject("Base.java", "public class Base { private int hidden; }")
    myFixture.addFileToProject("Child.java", "public class Child extends Base {}")

    safeDelete(findClass("Base"))

    assertNull(JavaPsiFacade.getInstance(project).findClass("Base", GlobalSearchScope.allScope(project)))
    assertEmpty(findClass("Child").extendsListTypes)
  }

  fun testSealedSuperclassIsDeletedWithItsPermits() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    myFixture.addFileToProject("Base.java", "public sealed class Base permits Child {}")
    myFixture.addFileToProject("Child.java", "public final class Child extends Base {}")

    safeDelete(findClass("Base"))

    assertNull(JavaPsiFacade.getInstance(project).findClass("Base", GlobalSearchScope.allScope(project)))
    assertEmpty(findClass("Child").extendsListTypes)
  }

  fun testSealedHierarchyIsPlannedAndWrittenAsInLegacy() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    val file = myFixture.addFileToProject("Hierarchy.java", """
      sealed interface GrandParent1 permits Parent, A {}

      sealed class GrandParent2 permits Parent {}

      sealed class Parent extends GrandParent2 implements GrandParent1 permits A, B {}

      non-sealed class A extends Parent implements GrandParent1 {}

      final class B extends Parent {}
      """.trimIndent())

    safeDelete(findClass("Parent"))

    assertEquals("""
      sealed interface GrandParent1 permits A, B {}

      sealed class GrandParent2 permits A, B {}

      non-sealed class A extends GrandParent2 implements GrandParent1 {}

      final class B extends GrandParent2 implements GrandParent1 {}
      """.trimIndent(), file.text)
  }

  fun testSealedParentOfTheDeletedClassPermitsTheHeir() {
    checkSealedParentPermitsTheHeir("Shape", "Impl")
  }

  fun testSealedParentOfTheDeletedClassPermitsTheHeirWhenItsFileSortsAfter() {
    checkSealedParentPermitsTheHeir("ZShape", "AImpl")
  }

  private fun checkSealedParentPermitsTheHeir(shape: String, impl: String) = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    myFixture.addFileToProject("$shape.java", "public sealed interface $shape permits Mid {}")
    myFixture.addFileToProject("Mid.java", "public non-sealed class Mid implements $shape {}")
    myFixture.addFileToProject("$impl.java", "public final class $impl extends Mid {}")

    safeDelete(findClass("Mid"))

    val sealedClass = findClass(shape)
    assertTrue(sealedClass.text, sealedClass.hasModifierProperty(PsiModifier.SEALED))
    assertEquals(listOf(impl), sealedClass.permitsListTypes.map { it.className })
    assertEquals(listOf(shape), findClass(impl).implementsListTypes.map { it.className })
  }

  fun testClassIsRemovedFromThePermitsOfAParentInTheSameFile() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    myFixture.addFileToProject("Shapes.java", """
      public sealed interface Shape permits Circle, Square {}
      final class Circle implements Shape {}
      final class Square implements Shape {}
      """.trimIndent())

    safeDelete(findClass("Circle"))

    val shape = findClass("Shape")
    assertTrue(shape.text, shape.hasModifierProperty(PsiModifier.SEALED))
    assertEquals(listOf("Square"), shape.permitsListTypes.map { it.className })
  }

  fun testClassIsRemovedFromThePermitsOfAParentInAnotherFile() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    myFixture.addFileToProject("Shape.java", "public sealed interface Shape permits Circle, Square {}")
    myFixture.addFileToProject("Circle.java", "public final class Circle implements Shape {}")
    myFixture.addFileToProject("Square.java", "public final class Square implements Shape {}")

    safeDelete(findClass("Circle"))

    val shape = findClass("Shape")
    assertTrue(shape.text, shape.hasModifierProperty(PsiModifier.SEALED))
    assertEquals(listOf("Square"), shape.permitsListTypes.map { it.className })
  }

  fun testTheLastPermittedClassDropsSealed() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_17) {
    myFixture.addFileToProject("Shape.java", "public sealed interface Shape permits Circle {}")
    myFixture.addFileToProject("Circle.java", "public final class Circle implements Shape {}")

    safeDelete(findClass("Circle"))

    val shape = findClass("Shape")
    assertFalse(shape.text, shape.hasModifierProperty(PsiModifier.SEALED))
    assertEmpty(shape.permitsListTypes)
  }

  fun testJavadocSeeReferenceIsRebound() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        /** @see #run(int, String) */
        void caller() {}
        void run(int unused, String keep) {}
      }
      """.trimIndent())

    safeDelete(method("Worker", "run").parameterList.parameters[0])

    val text = findClass("Worker").containingFile.text
    assertTrue(text, text.contains("#run(String)"))
    assertFalse(text, text.contains("#run(int"))
  }

  fun testJavadocLinkToAMethodOfAnotherClassKeepsTheClass() {
    myFixture.addFileToProject("Worker.java", "public class Worker { void run(int unused, String keep) {} }")
    val caller = myFixture.addFileToProject("Caller.java", """
      /** {@link Worker#run(int, String)} */
      public class Caller {}
      """.trimIndent())

    safeDelete(method("Worker", "run").parameterList.parameters[0])

    assertTrue(caller.text, caller.text.contains("Worker#run(String)"))
  }

  fun testMethodReferenceArgumentIsRemoved() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        interface Handler { void handle(int first, String second); }

        void run(int unused, String keep) {}
        void caller() { Handler handler = this::run; }
      }
      """.trimIndent())

    val readyPlan = ready(method("Worker", "run").parameterList.parameters[0])
    assertEquals(1, readyPlan.conflicts.conflicts.size)
    assertInstanceOf(ModPlanExecutor.execute(readyPlan.execution, true), ModPlanExecutionResult.Applied::class.java)

    val worker = findClass("Worker")
    assertFalse(worker.containingFile.text, worker.containingFile.text.contains("this::run"))
    val call = PsiTreeUtil.findChildrenOfType(worker, PsiMethodCallExpression::class.java).single { it.methodExpression.referenceName == "run" }
    assertEquals(1, call.argumentList.expressionCount)
  }

  fun testExternalAnnotationsAreDeleted() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void annotated() {}
      }
      """.trimIndent())
    val annotations = myFixture.addFileToProject("annotations.xml", """
      <root>
        <item name="Worker void annotated()">
          <annotation name="org.jetbrains.annotations.Contract"/>
        </item>
      </root>
      """.trimIndent())
    project.replaceService(ExternalAnnotationsManager::class.java, FakeAnnotationsManager(project, annotations), testRootDisposable)

    safeDelete(method("Worker", "annotated"))

    assertEmpty(findClass("Worker").findMethodsByName("annotated", false))
    assertFalse(annotations.text, annotations.text.contains("annotated"))
  }

  /** The annotations file of the package does not make a conflict for an element that it does not name. */
  fun testElementThatNoAnnotationsFileNamesIsNoConflict() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void plain() {}
      }
      """.trimIndent())
    val annotations = myFixture.addFileToProject("annotations.xml", "<root/>")
    project.replaceService(ExternalAnnotationsManager::class.java, FakeAnnotationsManager(project, annotations), testRootDisposable)

    safeDelete(method("Worker", "plain"))

    assertEmpty(findClass("Worker").findMethodsByName("plain", false))
  }

  /**
   * Every element gets the annotations file of the test, as the elements of a package get its file. Only an element that the file names has
   * an external annotation.
   */
  private class FakeAnnotationsManager(project: Project, private val file: PsiFile) : ExternalAnnotationsManagerImpl(project) {
    override fun findExternalAnnotationsFiles(listOwner: PsiModifierListOwner): List<PsiFile> = listOf(file)
  }

  fun testNonCodeUsageInCommentIsANoteAndStays() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void unused() {}
        // unused is mentioned here
        public void kept() {}
      }
      """.trimIndent())

    assertEmpty("the search in comments is off by default", ready(method("Worker", "unused")).conflicts.nonCodeUsages())
    val readyPlan = ready(method("Worker", "unused"), ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true))
    assertFalse("a non-code usage in a comment does not block the deletion", readyPlan.conflicts.needsConfirmation())
    assertEquals(1, readyPlan.conflicts.nonCodeUsages().size)
    apply(readyPlan)
    assertEmpty(findClass("Worker").findMethodsByName("unused", false))
    assertTrue("the comment stays", findClass("Worker").containingFile.text.contains("// unused is mentioned here"))
  }

  fun testNonCodeUsageInANonCodeFileIsANoteAndStays() {
    myFixture.addFileToProject("p/Worker.java", "package p; public class Worker {}")
    val notes = myFixture.addFileToProject("notes.txt", "p.Worker is mentioned here")

    val readyPlan = ready(findClass("p.Worker"), ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true))

    assertFalse("a non-code usage in a non-code file does not block the deletion", readyPlan.conflicts.needsConfirmation())
    assertEquals(notes.virtualFile, readyPlan.conflicts.nonCodeUsages().single().location!!.virtualFile)
    apply(readyPlan)
    assertNull(JavaPsiFacade.getInstance(project).findClass("p.Worker", GlobalSearchScope.allScope(project)))
    assertEquals("p.Worker is mentioned here", notes.text)
  }

  /** As in the legacy flow, the export of the package that has no class left stays; the legacy flow only notifies about it. */
  fun testExportOfTheEmptiedPackageIsANoteAndStays() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_1_9) {
    val moduleInfo = myFixture.addFileToProject("module-info.java", "module M { exports pack1; }")
    myFixture.addFileToProject("pack1/First.java", "package pack1; public class First {}")

    val readyPlan = ready(findClass("pack1.First"))

    assertFalse("a non-code usage does not block the deletion", readyPlan.conflicts.needsConfirmation())
    assertEquals(moduleInfo.virtualFile, readyPlan.conflicts.nonCodeUsages().single().location!!.virtualFile)
    apply(readyPlan)
    assertEquals("module M { exports pack1; }", moduleInfo.text)
  }

  fun testStaticImportOfTheFieldGoesWithIt() {
    myFixture.addFileToProject("p/Constants.java", """
      package p;

      public class Constants {
        public static final int LIMIT = 1;
        public static final int KEPT = 2;
      }
      """.trimIndent())
    val worker = myFixture.addFileToProject("Worker.java", """
      import static p.Constants.LIMIT;

      public class Worker {}
      """.trimIndent())

    safeDelete(findClass("p.Constants").findFieldByName("LIMIT", false)!!)

    assertNull(findClass("p.Constants").findFieldByName("LIMIT", false))
    assertFalse(worker.text, worker.text.contains("import static"))
  }

  fun testFieldWriteWithASideEffectIsAConflict() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        private int value;
        void update() { value = compute(); }
        int compute() { return 1; }
      }
      """.trimIndent())

    val readyPlan = ready(findClass("Worker").findFieldByName("value", false)!!)

    assertTrue("the assignment calls a method, so it is not deleted", readyPlan.conflicts.hasBroken())
    assertNotNull(findClass("Worker").findFieldByName("value", false))
  }

  fun testEditInAnotherFileKeepsThePlan() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void unused() {}
      }
      """.trimIndent())
    val otherFile = myFixture.addFileToProject("Other.java", "public class Other {}")

    val readyPlan = ready(method("Worker", "unused"))
    appendEdit(otherFile)
    apply(readyPlan)

    assertEmpty(findClass("Worker").findMethodsByName("unused", false))
  }

  fun testEditInAFileOfThePlanMakesThePlanStale() {
    val worker = myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void unused() {}
      }
      """.trimIndent())

    val readyPlan = ready(method("Worker", "unused"))
    appendEdit(worker)

    assertInstanceOf(ModPlanExecutor.execute(readyPlan.execution, true), ModPlanExecutionResult.Stale::class.java)
    assertSize(1, findClass("Worker").findMethodsByName("unused", false))
  }

  /** An unsaved edit does not change the stamp of the file, only the stamp of its document. */
  fun testUnsavedEditOfADeletedFileMakesThePlanStale() {
    val file = myFixture.addFileToProject("Single.java", "public class Single {}")

    val readyPlan = ready(findClass("Single"))
    appendEdit(file)

    assertInstanceOf(ModPlanExecutor.execute(readyPlan.execution, true), ModPlanExecutionResult.Stale::class.java)
    assertTrue(file.virtualFile.isValid)
  }

  private fun appendEdit(file: PsiFile) {
    WriteCommandAction.runWriteCommandAction(project) {
      val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
      document.insertString(document.textLength, "\n// edited")
    }
  }

  fun testRefactoringListenersAreNotified() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void unused() {}
      }
      """.trimIndent())
    val events = mutableListOf<String>()
    // As SafeDeleteProcessor does, the events come inside the command and outside the write action.
    fun state() = "command=${CommandProcessor.getInstance().currentCommand != null} write=${ApplicationManager.getApplication().isWriteAccessAllowed}"
    project.messageBus.connect(testRootDisposable).subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        events += "started $refactoringId ${state()}"
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        events += "done $refactoringId ${state()}"
      }
    })

    safeDelete(method("Worker", "unused"))

    assertEquals(listOf("started refactoring.safeDelete command=true write=false", "done refactoring.safeDelete command=true write=false"),
                 events)
  }

  fun testMethodThatOverridesAsksAboutItsBaseMethods() {
    addBaseWithOverride()

    fun asksAboutSuperMethods(method: PsiMethod) = questionsOf(method).any {
      it is ModPlanQuestion.Custom && it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS)
    }

    assertTrue(asksAboutSuperMethods(method("Impl", "run")))
    assertFalse(asksAboutSuperMethods(method("Base", "run")))
  }

  fun testOverridersInLocalAndAnonymousClassesAreAsked() {
    addHandlerWithLocalAndAnonymousOverriders()

    val askedQuestions = questionsOf(abstractHandle()).filterIsInstance<ModPlanQuestion.Custom>()
      .filter { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS) }

    assertSize(1, askedQuestions)
  }

  fun testOverridersInLocalAndAnonymousClassesAreDeletedByDefault() {
    addHandlerWithLocalAndAnonymousOverriders()

    safeDelete(abstractHandle())

    assertEquals(0, StringUtil.getOccurrenceCount(findClass("Anon").containingFile.text, "void handle()"))
  }

  fun testOverridersInLocalAndAnonymousClassesAreKeptWhenTheOptionSaysSo() {
    addHandlerWithLocalAndAnonymousOverriders()

    safeDelete(abstractHandle(), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false))

    assertEquals(2, StringUtil.getOccurrenceCount(findClass("Anon").containingFile.text, "void handle()"))
  }

  private fun abstractHandle(): PsiMethod =
    findClass("Anon").findInnerClassByName("Handler", false)!!.findMethodsByName("handle", false).single()

  private fun addHandlerWithLocalAndAnonymousOverriders() {
    myFixture.addFileToProject("Anon.java", """
      public class Anon {
        abstract static class Handler {
          abstract void handle();
        }

        void run() {
          class LocalHandler extends Handler {
            @Override
            void handle() {}
          }
          new Handler() {
            @Override
            void handle() {}
          };
        }
      }
      """.trimIndent())
  }

  fun testBaseMethodOfALibraryIsRefused() {
    myFixture.addFileToProject("Named.java", """
      public class Named {
        @Override
        public String toString() { return "named"; }
      }
      """.trimIndent())
    val toString = method("Named", "toString")

    val refusedPlan = plan(toString, ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true))

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE,
                 assertInstanceOf(refusedPlan, ModPlan.RefusedPlan::class.java).reason)
    assertInstanceOf(plan(toString, ModPlanOptionValues.EMPTY), ModPlan.AcceptedPlan::class.java)
  }

  /** The overriding methods of the base method are a question only after the base method is chosen, so the questions are asked again. */
  fun testOverridersOfTheBaseMethodAreAskedOnceTheBaseMethodIsChosen() {
    addHandlerWithLocalAndAnonymousOverriders()
    val localMethod = PsiTreeUtil.findChildrenOfType(method("Anon", "run"), PsiMethod::class.java).single { it.containingClass?.name == "LocalHandler" }
    fun asksAboutOverriders(optionValues: ModPlanOptionValues) = questionsOf(localMethod, optionValues).any {
      it is ModPlanQuestion.Custom && it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS)
    }

    assertFalse(asksAboutOverriders(ModPlanOptionValues.EMPTY))
    assertTrue(asksAboutOverriders(ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)))
  }

  /**
   * The question ends the refactoring at once for a base method of a library, before the questions are asked again and anything is
   * searched.
   */
  fun testChoosingTheBaseMethodOfALibraryEndsTheQuestion() {
    myFixture.addFileToProject("Named.java", """
      public class Named {
        @Override
        public String toString() { return "named"; }
      }
      """.trimIndent())
    val question = questionsOf(method("Named", "toString")).filterIsInstance<ModPlanQuestion.Custom>()
      .single { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) }

    val message = try {
      question.ask(project, ModPlanOptionValues.EMPTY)
      null
    }
    catch (e: RuntimeException) {
      e.message
    }

    assertTrue(message, message != null && message.contains("read-only"))
  }

  fun testChoosingTheBaseMethodOfTheProjectSetsTheOption() {
    addBaseWithOverride()
    val question = questionsOf(method("Impl", "run")).filterIsInstance<ModPlanQuestion.Custom>()
      .single { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) }

    val answers = question.ask(project, ModPlanOptionValues.EMPTY)!!

    assertFalse("the dialog leaves the general answer to the request", answers.has(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS))
    assertEquals(runReadActionBlocking { PsiFormatUtil.getExternalName(method("Impl", "run")) }, answers.get(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
  }

  fun testBaseMethodIsKeptByDefault() {
    addBaseWithOverride()

    safeDelete(method("Impl", "run"))

    assertEmpty(findClass("Impl").findMethodsByName("run", false))
    assertSize(1, findClass("Base").findMethodsByName("run", false))
  }

  fun testBaseMethodIsDeletedWithTheOverrideWhenTheOptionSaysSo() {
    addBaseWithOverride()

    safeDelete(method("Impl", "run"), ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true))

    assertEmpty(findClass("Impl").findMethodsByName("run", false))
    assertEmpty(findClass("Base").findMethodsByName("run", false))
  }

  /**
   * Both interface methods are deleted in one request, and each one's usages are preprocessed on their own. The kept overrider `S.run`
   * is an overrider of both, so each deletion sees it, and `M.run` keeps `@Override`, as in the legacy flow over the usages of both.
   */
  fun testOverrideStaysWhenTheOverriderOfTwoDeletedMethodsIsKept() {
    addOverriderOfTwoInterfaces()

    safeDelete(listOf(method("I1", "run"), method("I2", "run")),
               ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false))

    assertSize(1, findClass("S").findMethodsByName("run", false))
    assertTrue(method("M", "run").hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE))
  }

  fun testOverrideIsDroppedWhenTheOverriderOfTwoDeletedMethodsIsDeleted() {
    addOverriderOfTwoInterfaces()

    safeDelete(listOf(method("I1", "run"), method("I2", "run")))

    assertEmpty(findClass("S").findMethodsByName("run", false))
    assertFalse(method("M", "run").hasAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE))
  }

  /** `S.run` is unused and overrides both interface methods; `M.run` is used and overrides only `S.run`. */
  private fun addOverriderOfTwoInterfaces() {
    myFixture.addFileToProject("Hierarchy.java", """
      interface I1 { void run(); }
      interface I2 { void run(); }
      class S implements I1, I2 { public void run() {} }
      class M extends S { @Override public void run() {} }
      class User { void use(M m) { m.run(); } }
      """.trimIndent())
  }

  fun testPackageIsNotSupported() {
    myFixture.addFileToProject("p/A.java", "package p; public class A {}")
    val aPackage = JavaPsiFacade.getInstance(project).findPackage("p")!!

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, refusalOf(aPackage).reason)
  }

  fun testDefaultConstructorDeletesItsClass() {
    val aClass = myFixture.addClass("public class A {}")

    safeDelete(LightDefaultConstructor.create(aClass)!!)

    assertNull(JavaPsiFacade.getInstance(project).findClass("A", GlobalSearchScope.allScope(project)))
  }

  /** The usages of the class are searched, not only the calls of the constructor. */
  fun testDefaultConstructorFindsTheUsagesOfItsClass() {
    val aClass = myFixture.addClass("public class A {}")
    myFixture.addClass("public class B { A a; }")

    val readyPlan = ready(LightDefaultConstructor.create(aClass)!!)

    assertTrue("the type reference must be a conflict", readyPlan.conflicts.hasBroken())
    assertNotNull(JavaPsiFacade.getInstance(project).findClass("A", GlobalSearchScope.allScope(project)))
  }

  /** A quick fix or an action of a language gets the safe delete as a command, with the options of the request as the answers. */
  fun testCommandOfAnUnusedMethodIsRunByAModCommandAction() {
    myFixture.configureByText("Worker.java", """
      public class Worker {
        public void <caret>unused() {}
        public void kept() {}
      }
      """.trimIndent())

    myFixture.launchAction(SafeDeleteMethodAtCaretAction().asIntention())

    val text = myFixture.file.text
    assertFalse(text, text.contains("unused"))
    assertTrue(text, text.contains("kept"))
  }

  fun testCommandOfAUsedMethodStartsWithTheConflicts() {
    myFixture.configureByText("Worker.java", """
      public class Worker {
        public void <caret>used() {}
        public void caller() { used(); }
      }
      """.trimIndent())

    val context = ActionContext.from(myFixture.editor, myFixture.file)
    val command = inBackground { SafeDeleteMethodAtCaretAction().perform(context) }

    assertInstanceOf(command.unpack().first(), ModShowConflicts::class.java)
  }

  fun testCommandOfAPackageIsAnError() {
    val psiFile = myFixture.addFileToProject("p/A.java", "package p; public class A {}")
    val aPackage = JavaPsiFacade.getInstance(project).findPackage("p")!!
    val request = requestOf(aPackage, ModPlanOptionValues.EMPTY)

    val command = inBackground {
      ModPlanCommandAction(ModPlanSafeDeleteRefactoring.getInstance(),
                           request,
                           aPackage,
                           "",
                           false).perform(ActionContext.from(editor, psiFile))

    }
    assertEquals(RefactoringBundle.message("cannot.perform.refactoring"), assertInstanceOf(command, ModDisplayMessage::class.java).messageText())
  }

  private class SafeDeleteMethodAtCaretAction : ModCommandAction {
    override fun getFamilyName(): String = "Safe delete the method"

    override fun getPresentation(context: ActionContext): Presentation = Presentation.of(familyName)

    override fun perform(context: ActionContext): ModCommand {
      val method = PsiTreeUtil.getParentOfType(context.findLeaf(), PsiMethod::class.java)!!
      val modCommand = ModPlanCommandAction(ModPlanSafeDeleteRefactoring.getInstance(),
                                            ModPlanRequest(context.project(), listOf(SymbolPointer.of(method)), ModPlanOptionValues.EMPTY),
                                            method,
                                            "",
                                            false).perform(context)
      return modCommand
    }
  }

  fun testSuperMethodIsNotAskedWithoutRelated() {
    addBaseWithOverride()
    val run = method("Impl", "run")

    val askedQuestions = questionsOf(run, related = false).filterIsInstance<ModPlanQuestion.Custom>()
      .filter { it.options().contains(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) }
    val readyPlan = ready(run, related = false)

    assertEmpty(askedQuestions)
    assertEmpty(deletedFiles(readyPlan))
    apply(readyPlan)
    assertEmpty(findClass("Impl").findMethodsByName("run", false))
    assertSize(1, findClass("Base").findMethodsByName("run", false))
  }

  fun testAccessorsAreStillAskedWithoutRelated() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String name;
        public String getName() { return name; }
      }
      """.trimIndent())

    assertTrue(asksAbout(questionsOf(findClass("Bean").findFieldByName("name", false)!!, related = false),
                         ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS))
  }

  fun testEmptyElementsToSearchIsARefusal() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void refused() {}
      }
      """.trimIndent())
    val delegate = EmptySearchDelegate()
    SafeDeleteProcessorDelegate.EP_NAME.point.registerExtension(delegate, LoadingOrder.FIRST, testRootDisposable)
    ModPlanSafeDeleteProvider.EP_NAME.point.registerExtension(ModPlanLegacySafeDeleteProvider(delegate), LoadingOrder.FIRST, testRootDisposable)

    assertEquals(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, refusalOf(method("Worker", "refused")).reason)
  }

  /** Answers as Ruby FactoryBot answers for an unresolved model: with nothing to delete. */
  private class EmptySearchDelegate : JavaSafeDeleteProcessor() {
    override fun handlesElement(element: PsiElement?): Boolean = element is PsiMethod && element.name == "refused"

    override fun getElementsToSearch(element: PsiElement, module: Module?, allElementsToDelete: Collection<PsiElement>): Collection<PsiElement> =
      emptyList()
  }

  fun testSuperMethodQuestionThatTheRequestAnswersIsNotAsked() {
    addBaseWithOverride()
    val run = method("Impl", "run")
    val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true)
      .with(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF, externalNameOf(run))

    assertTrue(asksAbout(questionsOf(run), ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    assertFalse(asksAbout(questionsOf(run, answeredValues), ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    safeDelete(run, answeredValues)
    assertEmpty(findClass("Base").findMethodsByName("run", false))
  }

  /** The names of the answers add up, so a list of other methods does not answer the question of this one. */
  fun testSuperMethodQuestionIsAskedWhenTheRequestNamesOnlyAnotherMethod() {
    addTwoMethodsWithBases()
    val otherValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF, externalNameOf(method("Impl2", "b")))

    assertTrue(asksAbout(questionsOf(method("Impl1", "a"), otherValues), ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
  }

  /** The answer about the accessors of one field does not answer the question of another field. */
  fun testAccessorQuestionOfOneFieldLeavesTheOtherFieldAsked() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String a;
        private String b;
        public String getA() { return a; }
        public String getB() { return b; }
      }
      """.trimIndent())
    val a = findClass("Bean").findFieldByName("a", false)!!
    val b = findClass("Bean").findFieldByName("b", false)!!
    val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanJavaSafeDeleteOptions.ACCESSORS_OF, listOf(externalNameOf(a)), emptyList())

    assertTrue(asksAbout(questionsOf(b, askedValues), ModPlanJavaSafeDeleteOptions.ACCESSORS_OF))
    assertFalse(asksAbout(questionsOf(a, askedValues), ModPlanJavaSafeDeleteOptions.ACCESSORS_OF))
  }

  /** The answer about the hierarchy of one parameter does not answer the question of the parameter of another method. */
  fun testHierarchyQuestionOfOneParameterLeavesTheOtherParameterAsked() {
    myFixture.addFileToProject("Base1.java", "public class Base1 { public void run(int p) {} }")
    myFixture.addFileToProject("Impl1.java", "public class Impl1 extends Base1 { @Override public void run(int p) {} }")
    myFixture.addFileToProject("Base2.java", "public class Base2 { public void run(int q) {} }")
    myFixture.addFileToProject("Impl2.java", "public class Impl2 extends Base2 { @Override public void run(int q) {} }")
    val p = method("Base1", "run").parameterList.parameters[0]
    val q = method("Base2", "run").parameterList.parameters[0]
    val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanSafeDeleteOptions.HIERARCHY_OF, listOf(externalNameOf(p)), emptyList())

    assertTrue(asksAbout(questionsOf(q, askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
    assertFalse(asksAbout(questionsOf(p, askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
  }

  /** A type parameter has the key of its method and its name, so two type parameters of one method are asked about on their own. */
  fun testHierarchyQuestionOfOneTypeParameterLeavesTheOtherTypeParameterAsked() {
    myFixture.addFileToProject("Base.java", "public class Base { public <T, U> void run() {} }")
    myFixture.addFileToProject("Impl.java", "public class Impl extends Base { @Override public <T, U> void run() {} }")
    val run = method("Base", "run")
    val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanSafeDeleteOptions.HIERARCHY_OF, listOf(externalNameOf(run) + " T"), emptyList())

    assertTrue(asksAbout(questionsOf(run.typeParameters[1], askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
    assertFalse(asksAbout(questionsOf(run.typeParameters[0], askedValues), ModPlanSafeDeleteOptions.HIERARCHY_OF))
  }

  /**
   * The legacy flow passes the usages of every language to this delegate, also under a read lock: it must not open a modal progress
   * there.
   */
  fun testPreprocessUsagesUnderAReadLock() {
    myFixture.addFileToProject("Worker.java", "public class Worker { int unused; void use() { unused++; } }")
    val usages = runReadActionBlocking {
      val field = findClass("Worker").findFieldByName("unused", false)!!
      val reference = PsiTreeUtil.findChildOfType(method("Worker", "use"), PsiReferenceExpression::class.java)!!
      arrayOf<UsageInfo>(SafeDeleteReferenceSimpleDeleteUsageInfo(reference, field, false))
    }

    val processedUsages = runReadActionBlocking { JavaSafeDeleteProcessor().preprocessUsages(project, usages) }

    assertSameElements(processedUsages!!.toList(), usages.toList())
  }

  /** The switch is the general answer of a request: it answers the question of every method. */
  fun testSuperMethodsSwitchAloneAnswersTheQuestion() {
    addBaseWithOverride()
    val keepValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, false)

    assertFalse(asksAbout(questionsOf(method("Impl", "run"), keepValues), ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    safeDelete(method("Impl", "run"), keepValues)
    assertSize(1, findClass("Base").findMethodsByName("run", false))
  }

  /** A method that the dialog asked about with the answer no is not asked again, and keeps its base method. */
  fun testAskedMethodThatIsNotChosenKeepsItsBaseMethod() {
    addBaseWithOverride()
    val askedValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF, listOf(externalNameOf(method("Impl", "run"))), emptyList())

    assertFalse(asksAbout(questionsOf(method("Impl", "run"), askedValues), ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF))
    safeDelete(method("Impl", "run"), askedValues)
    assertSize(1, findClass("Base").findMethodsByName("run", false))
  }

  fun testHierarchyQuestionThatTheRequestAnswersIsNotAsked() {
    myFixture.addFileToProject("Base.java", "public class Base { public void run(int unused) {} }")
    myFixture.addFileToProject("Impl.java", "public class Impl extends Base { @Override public void run(int unused) {} }")
    val parameter = method("Base", "run").parameterList.parameters[0]

    assertTrue(asksAbout(questionsOf(parameter), ModPlanSafeDeleteOptions.DELETE_HIERARCHY))
    assertFalse(asksAbout(questionsOf(parameter, ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.DELETE_HIERARCHY, false)),
                          ModPlanSafeDeleteOptions.DELETE_HIERARCHY))
  }

  fun testAccessorQuestionThatTheRequestAnswersIsNotAsked() {
    myFixture.addFileToProject("Bean.java", """
      public class Bean {
        private String name;
        public String getName() { return name; }
      }
      """.trimIndent())
    val field = findClass("Bean").findFieldByName("name", false)!!

    assertTrue(asksAbout(questionsOf(field), ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS))
    assertFalse(asksAbout(questionsOf(field, ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS, true)),
                          ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS))
  }

  fun testOverridersQuestionThatTheRequestAnswersIsNotAsked() {
    addBaseWithOverride()
    val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, externalNameOf(method("Impl", "run")))

    assertTrue(asksAbout(questionsOf(method("Base", "run")), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
    assertFalse(asksAbout(questionsOf(method("Base", "run"), answeredValues), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
  }

  fun testOverridersQuestionIsAskedWhenTheRequestNamesOnlyOtherMethods() {
    addTwoMethodsWithBases()
    val otherValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, externalNameOf(method("Impl2", "b")))

    assertTrue(asksAbout(questionsOf(method("Base1", "a"), otherValues), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
  }

  fun testCallersQuestionThatTheRequestAnswersIsNotAsked() {
    addCallersThatPassOnTheParameter()
    val parameter = method("Worker", "bar").parameterList.parameters[0]
    val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)
      .with(ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN, externalNameOf(method("Worker", "foo")))

    assertTrue(asksAbout(questionsOf(parameter), ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN))
    assertFalse(asksAbout(questionsOf(parameter, answeredValues), ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN))
  }

  fun testCallersQuestionIsAskedWhenTheRequestNamesOnlyOtherMethods() {
    addCallersThatPassOnTheParameter()
    val otherValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN, externalNameOf(method("Worker", "main")))

    assertTrue(asksAbout(questionsOf(method("Worker", "bar").parameterList.parameters[0], otherValues),
                         ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN))
  }

  fun testCalleesQuestionThatTheRequestAnswersIsNotAsked() {
    addCallerWithTwoCallees()
    val answeredValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true)
      .with(ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE, externalNameOf(method("Worker", "first")))

    assertTrue(asksAbout(questionsOf(method("Worker", "caller")), ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE))
    assertFalse(asksAbout(questionsOf(method("Worker", "caller"), answeredValues), ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE))
  }

  fun testCalleesQuestionIsAskedWhenTheRequestNamesOnlyOtherMethods() {
    addCallerWithTwoCallees()
    val otherValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE, externalNameOf(method("Worker", "caller")))

    assertTrue(asksAbout(questionsOf(method("Worker", "caller"), otherValues), ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE))
  }

  /** The switch is the general answer of a request: it answers the question, and its value decides for all the offered elements. */
  fun testOverridersSwitchAloneAnswersTheQuestion() {
    addBaseWithOverride()
    val keepValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false)

    assertFalse(asksAbout(questionsOf(method("Base", "run"), keepValues), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
    safeDelete(method("Base", "run"), keepValues)
    assertSize(1, findClass("Impl").findMethodsByName("run", false))
  }

  fun testCalleesSwitchAloneAnswersTheQuestionAndDeletesThemAll() {
    addCallerWithTwoCallees()
    val deleteValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, true)

    assertFalse(asksAbout(questionsOf(method("Worker", "caller"), deleteValues), ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE))
    safeDelete(method("Worker", "caller"), deleteValues)
    assertEmpty(findClass("Worker").findMethodsByName("first", false))
    assertEmpty(findClass("Worker").findMethodsByName("second", false))
  }

  fun testCallersSwitchAloneAnswersTheQuestionAndDeletesInThemAll() {
    addCallersThatPassOnTheParameter()
    val parameter = method("Worker", "bar").parameterList.parameters[0]
    val deleteValues = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, true)

    assertFalse(asksAbout(questionsOf(parameter, deleteValues), ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN))
    safeDelete(parameter, deleteValues)
    assertEquals(0, method("Worker", "foo").parameterList.parametersCount)
    assertEquals(0, method("Worker", "baz").parameterList.parametersCount)
  }

  /** A dialog that chose nothing names the offered overriding methods only: the question is answered, and they stay. */
  fun testOfferedOverridersThatAreNotChosenStay() {
    addBaseWithOverride()
    val offeredValues = ModPlanOptionValues.EMPTY.withAnswers(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, listOf(externalNameOf(method("Impl", "run"))), emptyList())

    assertFalse(asksAbout(questionsOf(method("Base", "run"), offeredValues), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
    safeDelete(method("Base", "run"), offeredValues)
    assertSize(1, findClass("Impl").findMethodsByName("run", false))
  }

  /** The answer of the dialog of one method does not answer the dialog of another method, and does not choose its overriding methods. */
  fun testAnswerAboutTheOverridersOfOneMethodLeavesTheOtherMethodAsked() {
    addTwoMethodsWithBases()
    val firstName = externalNameOf(method("Impl1", "a"))
    val firstAnswers = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, firstName)

    assertTrue(asksAbout(questionsOf(method("Base2", "b"), firstAnswers), ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
    safeDelete(listOf(method("Base1", "a"), method("Base2", "b")), firstAnswers)
    assertEmpty(findClass("Impl1").findMethodsByName("a", false))
    assertSize(1, findClass("Impl2").findMethodsByName("b", false))
  }

  /** An overriding method of two deleted methods does not answer the question of the second one, which offers one more. */
  fun testSharedOverriderLeavesTheOtherMethodAsked() {
    myFixture.addFileToProject("I.java", "public interface I { void foo(); }")
    myFixture.addFileToProject("J.java", "public interface J { void foo(); }")
    myFixture.addFileToProject("C.java", "public class C implements I, J { @Override public void foo() {} }")
    myFixture.addFileToProject("D.java", "public class D implements J { @Override public void foo() {} }")
    val sharedName = externalNameOf(method("C", "foo"))
    val firstAnswers = ModPlanOptionValues.EMPTY.with(ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE, sharedName)

    assertTrue(asksAbout(questionsOf(listOf(method("I", "foo"), method("J", "foo")), firstAnswers),
                         ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE))
  }

  private fun addCallerWithTwoCallees() {
    myFixture.addFileToProject("Worker.java", """
      public class Worker {
        public void caller() { first(); second(); }
        private void first() {}
        private void second() {}
      }
      """.trimIndent())
  }

  fun testSearchFormIsNotAskedOnlyWhenTheRequestAnswersBothOptions() {
    myFixture.addFileToProject("Worker.java", "public class Worker { public void unused() {} }")
    val unused = method("Worker", "unused")
    val commentsOnly = ModPlanOptionValues.EMPTY.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true)

    assertTrue(asksAbout(questionsOf(unused), ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
    assertTrue(asksAbout(questionsOf(unused, commentsOnly), ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE))
    assertFalse(asksAbout(questionsOf(unused, commentsOnly.with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, false)),
                          ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS))
  }

  private fun asksAbout(questions: List<ModPlanQuestion>, option: ModPlanOption<*>): Boolean = questions.any { question ->
    when (question) {
      is ModPlanQuestion.Form -> option in question.options()
      is ModPlanQuestion.Custom -> option in question.options()
    }
  }

  private fun externalNameOf(owner: PsiModifierListOwner): String = runReadActionBlocking { PsiFormatUtil.getExternalName(owner) }!!

  private fun <T> inBackground(action: () -> T): T {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable { runReadActionBlocking(action) }))
  }

  private fun plan(elements: List<PsiElement>, optionValues: ModPlanOptionValues, related: Boolean = true): ModPlan =
    inBackground { ModPlanSafeDeleteRefactoring.getInstance().plan(requestOf(elements, optionValues, related)) }

  private fun plan(element: PsiElement, optionValues: ModPlanOptionValues, related: Boolean = true): ModPlan =
    plan(listOf(element), optionValues, related)

  private fun requestOf(elements: List<PsiElement>, optionValues: ModPlanOptionValues, related: Boolean = true): ModPlanRequest {
    val requestOptionValues = if (related) { optionValues } else { optionValues.with(ModPlanSafeDeleteOptions.SEARCH_RELATED, false) }
    return ModPlanRequest(project, elements.map(SymbolPointer::of), requestOptionValues)
  }

  private fun requestOf(element: PsiElement, optionValues: ModPlanOptionValues, related: Boolean = true): ModPlanRequest =
    requestOf(listOf(element), optionValues, related)

  private fun ready(
    element: PsiElement,
    optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY,
    related: Boolean = true,
  ): ModPlan.AcceptedPlan =
    assertInstanceOf(plan(element, optionValues, related), ModPlan.AcceptedPlan::class.java)

  private fun refusalOf(element: PsiElement): ModPlan.RefusedPlan =
    assertInstanceOf(plan(element, ModPlanOptionValues.EMPTY), ModPlan.RefusedPlan::class.java)

  private fun safeDelete(elements: List<PsiElement>, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY) {
    apply(assertInstanceOf(plan(elements, optionValues), ModPlan.AcceptedPlan::class.java))
  }

  private fun safeDelete(element: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY) {
    safeDelete(listOf(element), optionValues)
  }

  private fun apply(acceptedPlan: ModPlan.AcceptedPlan) {
    val messages = acceptedPlan.conflicts.conflicts.map { it.messages }
    assertFalse("unexpected conflicts: $messages", acceptedPlan.conflicts.hasBroken())
    assertInstanceOf(ModPlanExecutor.execute(acceptedPlan.execution, true), ModPlanExecutionResult.Applied::class.java)
  }

  private fun deletedFiles(acceptedPlan: ModPlan.AcceptedPlan): List<VirtualFile> =
    acceptedPlan.execution.command.unpack().filterIsInstance<ModDeleteFile>().map { it.file() }

  /** @return the questions that a headless run asks, in their order; the options of the request answer them */
  private fun questionsOf(elements: List<PsiElement>, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY, related: Boolean = true): List<ModPlanQuestion> {
    val askedQuestions = ArrayList<ModPlanQuestion>()
    inBackground {
      val request = requestOf(elements, optionValues, related)
      ModPlanTestUtil.runAnswering(request, ModPlanSafeDeleteRefactoring.getInstance()::chain) { question, currentValues ->
        askedQuestions.add(question)
        currentValues
      }
    }
    return askedQuestions
  }

  private fun questionsOf(element: PsiElement, optionValues: ModPlanOptionValues = ModPlanOptionValues.EMPTY, related: Boolean = true): List<ModPlanQuestion> =
    questionsOf(listOf(element), optionValues, related)

  private fun method(className: String, name: String) = findClass(className).findMethodsByName(name, false).single()

  private fun findClass(name: String): PsiClass =
    JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.allScope(project))!!
}
