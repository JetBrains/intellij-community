// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.usageView.UsageInfo
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.function.Function

/**
 * The fixture of the tests of the change signature refactoring. Create it with [changeSignatureFixture].
 *
 * Each `doTest` configures the file `<relative path><test name>.java` of the Java test data.
 * Then it changes the signature of the method that the target function finds, and compares the result with the file `<test name>_after.java`.
 * The target search and the refactoring run on the EDT in one command, as in the JUnit 3 test.
 */
class ChangeSignatureFixture internal constructor(
  val fixture: LightJavaCodeInsightTestFixture,
  private val relativePath: String,
  private val targetElement: Function<LightJavaCodeInsightTestFixture, PsiElement?>,
) {
  /**
   * The element factory of the project.
   */
  val factory: PsiElementFactory
    get() = JavaPsiFacade.getElementFactory(fixture.project)

  /**
   * Changes the return type to [returnType], the parameters to the types [parameters] with the names `p1`, `p2` and so on,
   * and the exceptions to the types [exceptions]. A null array keeps the parameters or the exceptions of the method.
   */
  fun doTest(returnType: String?, parameters: Array<String>?, exceptions: Array<String>?, delegate: Boolean) {
    val genParams = if (parameters == null) SimpleParameterGen()
    else GenParams { method ->
      Array(parameters.size) { i ->
        ParameterInfoImpl.createNew().withName("p" + (i + 1)).withType(factory.createTypeFromText(parameters[i], method))
      }
    }
    val genExceptions = if (exceptions == null) SimpleExceptionsGen()
    else GenExceptions { method ->
      Array(exceptions.size) { i ->
        JavaThrownExceptionInfo(-1, factory.createTypeFromText(exceptions[i], method) as PsiClassType)
      }
    }
    doTest(null, null, returnType, genParams, genExceptions, delegate)
  }

  fun doTest(newReturnType: String?, parameterInfos: Array<ParameterInfoImpl>, generateDelegate: Boolean) {
    doTest(null, null, newReturnType, parameterInfos, emptyArray(), generateDelegate)
  }

  fun doTest(
    newVisibility: String?,
    newName: String?,
    newReturnType: String?,
    parameterInfo: Array<ParameterInfoImpl>,
    exceptionInfo: Array<ThrownExceptionInfo>,
    generateDelegate: Boolean,
  ) {
    doTest(newVisibility, newName, newReturnType, SimpleParameterGen(parameterInfo), SimpleExceptionsGen(exceptionInfo), generateDelegate)
  }

  fun doTest(newVisibility: String?, newName: String?, newReturnType: String?, genParams: GenParams, generateDelegate: Boolean) {
    doTest(newVisibility, newName, newReturnType, genParams, SimpleExceptionsGen(), generateDelegate)
  }

  fun doTest(
    newVisibility: String?,
    newName: String?,
    newReturnType: String?,
    genParams: GenParams,
    genExceptions: GenExceptions,
    generateDelegate: Boolean,
  ) {
    doTest(newVisibility, newName, newReturnType, genParams, genExceptions, generateDelegate, false)
  }

  /**
   * With [skipConflict], a conflict of the refactoring does not stop it.
   */
  fun doTest(
    newVisibility: String?,
    newName: String?,
    newReturnType: String?,
    genParams: GenParams,
    genExceptions: GenExceptions,
    generateDelegate: Boolean,
    skipConflict: Boolean,
  ) {
    val basePath = relativePath + fixture.getTestName(false)
    fixture.configureByFile("$basePath.java")
    fixture.runInCommand {
      val target = targetElement.apply(fixture)
      assertTrue(target is PsiMethod, "<caret> is not on method name")
      val method = target as PsiMethod
      val newType = if (newReturnType != null) factory.createTypeFromText(newReturnType, method) else method.returnType
      object : ChangeSignatureProcessor(fixture.project, method, generateDelegate, newVisibility,
                                        newName ?: method.name,
                                        newType, genParams.genParams(method), genExceptions.genExceptions(method)) {
        override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
          try {
            return super.preprocessUsages(refUsages)
          }
          catch (e: ConflictsInTestsException) {
            if (skipConflict) {
              return true
            }
            throw e
          }
        }
      }.run()
      fixture.checkResultByFile(basePath + "_after.java")
    }
  }

  fun interface GenParams {
    fun genParams(method: PsiMethod): Array<ParameterInfoImpl>
  }

  fun interface GenExceptions {
    fun genExceptions(method: PsiMethod): Array<ThrownExceptionInfo>
  }

  /**
   * Gives [infos], or keeps the parameters of the method when [infos] is null.
   */
  class SimpleParameterGen @JvmOverloads constructor(private var infos: Array<ParameterInfoImpl>? = null) : GenParams {
    override fun genParams(method: PsiMethod): Array<ParameterInfoImpl> {
      val result = infos ?: Array(method.parameterList.parametersCount) { ParameterInfoImpl.create(it) }.also { infos = it }
      for (info in result) {
        info.updateFromMethod(method)
      }
      return result
    }
  }

  /**
   * Gives [infos]. The default is no exception.
   */
  class SimpleExceptionsGen @JvmOverloads constructor(private val infos: Array<ThrownExceptionInfo> = emptyArray()) : GenExceptions {
    override fun genExceptions(method: PsiMethod): Array<ThrownExceptionInfo> {
      for (info in infos) {
        info.updateFromMethod(method)
      }
      return infos
    }
  }

  companion object {
    /**
     * Returns the element at the caret of [fixture] that the change signature action finds by its name.
     */
    @JvmStatic
    fun findTargetElement(fixture: LightJavaCodeInsightTestFixture): PsiElement? =
      TargetElementUtil.findTargetElement(fixture.editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED)
  }
}

/**
 * Creates a [ChangeSignatureFixture] for one test in the project of [projectFixture].
 * The test data of the test is in the directory [relativePath] of the Java test data, for example `/refactoring/changeSignature/`.
 * [targetElement] finds the method to change. It runs on the EDT. The default is [ChangeSignatureFixture.findTargetElement].
 */
@JvmOverloads
fun changeSignatureFixture(
  projectFixture: TestFixture<LightJavaProject>,
  relativePath: String,
  targetElement: Function<LightJavaCodeInsightTestFixture, PsiElement?> = Function { ChangeSignatureFixture.findTargetElement(it) },
): TestFixture<ChangeSignatureFixture> = testFixture("changeSignatureFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData").init()
  initialized(ChangeSignatureFixture(fixture, relativePath, targetElement)) {}
}
