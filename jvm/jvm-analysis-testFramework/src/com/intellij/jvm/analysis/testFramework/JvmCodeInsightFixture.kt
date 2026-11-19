// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JvmCodeInsightFixtures")

package com.intellij.jvm.analysis.testFramework

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.ide.actions.GotoRelatedSymbolAction
import com.intellij.modcommand.ActionContext
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.psi.ResolveResult
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.function.Function

/**
 * The code insight fixture of a JVM test. It is the JUnit 5 form of [LightJvmCodeInsightFixtureTestCase].
 * Create it with [jvmCodeInsightFixture].
 *
 * It is a [LightJavaCodeInsightTestFixture], so a test calls the methods of [fixture] on it directly.
 * The helpers of [JvmGoToRelatedTestBase] and [JvmReferenceContributorTestBase] are members of it.
 * Call the helpers from any thread. They read the PSI in a read action.
 *
 * Kotlin delegation does not delegate the default methods of a Java interface, so the class overrides them to call [fixture].
 */
@TestOnly
open class JvmCodeInsightFixture(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) : LightJavaCodeInsightTestFixture by fixture {
  override fun getTestRootDisposable(): Disposable = fixture.testRootDisposable

  override fun getProjectDisposable(): Disposable = fixture.projectDisposable

  override fun getActionContext(): ActionContext = fixture.actionContext

  override fun performEditorAction(actionId: String): Unit = fixture.performEditorAction(actionId)

  override fun isOpenedInMyEditor(virtualFile: VirtualFile): Boolean = fixture.isOpenedInMyEditor(virtualFile)

  /**
   * Returns the name of the test, as [getTestName] with `false` returns it.
   */
  fun generateFileName(): String = getTestName(false).replace("[^a-zA-Z0-9\\.\\-]", "_")

  /**
   * Configures [before] in a file of [lang], and checks the single related item at the caret with [assertion].
   */
  fun testGoToRelatedAction(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (GotoRelatedItem) -> Unit,
  ) {
    configureByText("$fileName${lang.ext}", before)
    runReadActionBlocking {
      val items = GotoRelatedSymbolAction.getItems(file, editor)
      assertEquals(1, items.size, items.toString())
      assertion(items.first())
    }
  }

  /**
   * Configures [before] in a file of [lang], resolves the reference at the caret, and checks the reference and its target with [assertion].
   */
  fun assertResolvableReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference, PsiElement) -> Unit = { _, _ -> },
  ) {
    configureByText("$fileName${lang.ext}", before)
    runReadActionBlocking {
      val reference = file.findReferenceAt(caretOffset) ?: error("Could not find reference at caret offset")
      val resolved = reference.resolve()
      assertNotNull(resolved)
      assertion(reference, resolved!!)
    }
  }

  /**
   * Configures [before] in a file of [lang], and checks the results of the poly-variant reference at the caret with [assertion].
   */
  fun assertMultiresolveReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference, Array<ResolveResult>) -> Unit = { _, _ -> },
  ) {
    configureByText("$fileName${lang.ext}", before)
    runReadActionBlocking {
      val reference = file.findReferenceAt(caretOffset) ?: error("Could not find reference at caret offset")
      assertTrue(reference is PsiPolyVariantReference)
      if (reference is PsiPolyVariantReference) {
        assertion(reference, reference.multiResolve(false))
      }
    }
  }

  /**
   * Configures [before] in a file of [lang], checks that the reference at the caret does not resolve, and checks it with [assertion].
   */
  fun assertUnResolvableReference(
    lang: JvmLanguage,
    before: String,
    fileName: String = generateFileName(),
    assertion: (PsiReference) -> Unit = { _ -> },
  ) {
    configureByText("$fileName${lang.ext}", before)
    runReadActionBlocking {
      val reference = file.findReferenceAt(caretOffset) ?: error("Could not find reference at caret offset")
      assertNull(reference.resolve())
      assertion(reference)
    }
  }
}

/**
 * Returns the lookup strings of the variants of the reference. Call it in a read action.
 */
@TestOnly
fun PsiReference.lookupStringVariants(): List<String> {
  return variants.mapNotNull { if (it is LookupElement) it.lookupString else null }
}

/**
 * Creates the project of a JVM test class with the descriptor of [LightJvmCodeInsightFixtureTestCase]:
 * the mock JDK and the language level [LanguageLevel.HIGHEST]. Declare it in a companion object or in a static field.
 */
@TestOnly
fun jvmProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.ProjectDescriptor(LanguageLevel.HIGHEST))

/**
 * Creates a [JvmCodeInsightFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path. When it is null, the test data path is the community directory,
 * as in [LightJvmCodeInsightFixtureTestCase].
 * A class that picks the descriptor for each test gives [descriptor], as [lightJavaCodeInsightFixture] describes.
 */
@TestOnly
@JvmOverloads
fun jvmCodeInsightFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  descriptor: Function<String, LightProjectDescriptor>? = null,
): TestFixture<JvmCodeInsightFixture> = testFixture("jvmCodeInsightFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, descriptor).init()
  fixture.testDataPath = testDataPath ?: communityTestDataPath()
  initialized(JvmCodeInsightFixture(fixture)) {}
}

// The test data path of LightJvmCodeInsightFixtureTestCase with an empty base path.
internal fun communityTestDataPath(): String = FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath())
