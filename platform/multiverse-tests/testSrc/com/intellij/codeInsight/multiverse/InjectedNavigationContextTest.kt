// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/** IJPL-262777: Go to Declaration inside an injected fragment keeps the host editor in the context of the host file. */
@TestApplication
class InjectedNavigationContextTest : LightJavaCodeInsightFixtureTestCase5() {
  /** The test builds its file from text and needs no test data. */
  override fun getRelativePath(): String = ""

  override fun getTestDataPath(): String = ""

  @Test
  fun gotoDeclarationInInjectedFragmentKeepsHostContext() = timeoutRunBlocking(timeout = 3.minutes) {
    withContext(Dispatchers.EDT) {
      val project = fixture.project
      val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
      assertTrue(isSharedSourceSupportEnabled(project), "multiverse support must be enabled for this test")
      injectedLanguageManager.registerMultiHostInjector(JavaStringInjector, fixture.testRootDisposable)
      fixture.configureByText(
        "Host.java",
        """
        class Host {
          String sample = "class Sample { int v1 = 0; int v2 = v1<caret>; }";
        }
        """.trimIndent()
      )
      // The caret is inside the injected fragment, so the fixture gives the injected file and editor.
      val hostFile = injectedLanguageManager.getTopLevelFile(fixture.file)
      assertTrue(injectedLanguageManager.isInjectedFragment(fixture.file), "the caret must be inside the injected fragment")
      val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(fixture.editor)
      val hostContext = CodeInsightContextManager.getInstance(project).getCodeInsightContext(hostFile.viewProvider)
      assertNotEquals(defaultContext(), hostContext, "the host file must have a context of its own")

      fixture.performEditorAction("GotoDeclaration")

      assertEquals(hostFile.text.indexOf("v1 = 0"), hostEditor.caretModel.offset, "Go to Declaration must move the host caret to the declaration")
      assertEquals(hostContext, EditorContextManager.getEditorContext(hostEditor, project))
    }
  }
}

/** Injects the content of every Java string literal as Java. */
private object JavaStringInjector : MultiHostInjector {
  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val host = context as? PsiLanguageInjectionHost ?: return
    if (!host.isValidHost) return
    registrar.startInjecting(JavaLanguage.INSTANCE)
      .addPlace(null, null, host, ElementManipulators.getValueTextRange(host))
      .doneInjecting()
  }

  override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(PsiLiteralExpression::class.java)
}
