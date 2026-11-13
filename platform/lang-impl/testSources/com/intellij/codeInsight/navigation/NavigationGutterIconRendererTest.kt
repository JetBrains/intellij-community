// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.LightElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent
import java.lang.reflect.Proxy
import javax.swing.JPanel

@TestApplication
internal class NavigationGutterIconRendererTest {
  companion object {
    private val project = projectFixture()
    private val sourceRoot = project.moduleFixture().sourceRootFixture()
    private val file = sourceRoot.psiFileFixture("target.txt", "hello world")
    private val firstTarget = sourceRoot.psiFileFixture("first.txt", "first")
    private val secondTarget = sourceRoot.psiFileFixture("second.txt", "second")
  }

  @AfterEach
  fun clearInterceptors() {
    UiInterceptors.clear()
  }

  @Test
  fun `popup shows the targets with the presentation of the target renderer`(): Unit = timeoutRunBlocking {
    val chosen = CompletableDeferred<PsiElement>()
    val renderer = readAction {
      NavigationGutterIconBuilder.create(AllIcons.Nodes.Class)
        .setTargets(firstTarget.get(), secondTarget.get())
        .setTargetRenderer {
          object : PsiTargetPresentationRenderer<PsiElement>() {
            override fun getElementText(element: PsiElement): String = "custom " + (element as PsiFile).name
          }
        }
        .createGutterIconRenderer(project.get()) { _, element -> chosen.complete(element) }
    }
    UiInterceptors.register(ChooserInterceptor(listOf("custom first.txt", "custom second.txt"), "custom second\\.txt"))

    withContext(Dispatchers.EDT) {
      renderer.navigate(MouseEvent(JPanel(), MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, 1, false), null)
    }

    assertEquals(secondTarget.get(), chosen.await())
  }

  @Test
  fun `popup fallback resolves a non-navigatable target offset in background`(): Unit = timeoutRunBlocking {
    val navigatable = readAction {
      val psiFile = file.get()
      val leaf = requireNotNull(psiFile.findElementAt(6))
      val target = Proxy.newProxyInstance(PsiElement::class.java.classLoader, arrayOf(PsiElement::class.java)) { proxy, method, args ->
        when (method.name) {
          "getNavigationElement" -> proxy
          "getTextOffset" -> {
            assertFalse(ApplicationManager.getApplication().isDispatchThread)
            assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
            6
          }
          else -> method.invoke(leaf, *(args ?: emptyArray()))
        }
      } as PsiElement
      assertFalse(target is Navigatable)
      val origin = object : LightElement(psiFile.manager, psiFile.language) {
        override fun getContainingFile(): PsiFile = psiFile
        override fun getTextRange(): TextRange = TextRange(6, 11)
        override fun toString(): String = "Gutter target"
      }
      origin.setNavigationElement(target)
      NavigationGutterIconRenderer.createPopupNavigatable(origin)
    }

    readAction {
      val request = navigatable.navigationRequest() as? SourceNavigationRequest
      assertNotNull(request)
      assertEquals(file.get().virtualFile, request!!.file)
      assertEquals(6, request.offsetMarker?.startOffset)
    }
  }
}
