// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Inlay
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.junit.Assert

class AnnotationHintsTest: LightJavaCodeInsightFixtureTestCase() {

  fun `test contract inferred annotation`() {
    val text = """
class Demo {
  private static int pure(int x, int y) {
    return x * y + 10;
  }
}"""
    myFixture.configureByText("A.java", text)
    myFixture.doHighlighting()
    // until proper infrastructure to test hints appeared
    val provider = AnnotationInlayProvider()
    val inlays = collectInlays(provider, AnnotationInlayProvider.Settings(showInferred = true, showExternal = true))
    val blockInlays = inlays.blockElements

    Assert.assertEquals(1, blockInlays.size)
    assertEquals("[[@ Contract [( [[pure  =  true]] )]]]", (blockInlays.first().renderer as PresentationRenderer).presentation.toString())
  }

  private fun <T : Any> collectInlays(provider: InlayHintsProvider<T>, settings: T = provider.createSettings()): Inlays {
    val file = myFixture.file
    val key = provider.key
    val sink = InlayHintsSinkImpl(key)
    val editor = myFixture.editor
    val collector = provider.getCollectorFor(file, editor, settings, sink)!!
    val traverser = SyntaxTraverser.psiTraverser(file)
    traverser.forEach {
      collector.collect(it, editor, sink)
    }
    sink.applyToEditor(editor, MarkList(listOf()), MarkList(listOf()), true)
    val blockElements = editor.inlayModel.getBlockElementsInRange(0, file.textRange.endOffset)
    val inlineElements = editor.inlayModel.getBlockElementsInRange(0, file.textRange.endOffset)
    return Inlays(blockElements, inlineElements)
  }

  class Inlays(
    val blockElements: List<Inlay<*>>,
    val inlineElements: List<Inlay<*>>
  )
}