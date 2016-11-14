/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.DocumentUtil
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element


abstract class InlayParameterHintsTest : LightCodeInsightFixtureTestCase() {
  
  private var isParamHintsEnabledBefore = false
  private lateinit var stateBefore: Element

  override fun setUp() {
    super.setUp()
    
    val settings = EditorSettingsExternalizable.getInstance()
    isParamHintsEnabledBefore = settings.isShowParameterNameHints
    settings.isShowParameterNameHints = true
    
    stateBefore = ParameterNameHintsSettings.getInstance().state
  }

  override fun tearDown() {
    EditorSettingsExternalizable.getInstance().isShowParameterNameHints = isParamHintsEnabledBefore
    ParameterNameHintsSettings.getInstance().loadState(stateBefore)
    
    super.tearDown()
  }

  protected fun getInlays(): List<Inlay> {
    val editor = myFixture.editor
    return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)
  }

  protected fun onLineStartingWith(text: String): InlayAssert {
    val range = getLineRangeStartingWith(text)
    val inlays = myFixture.editor.inlayModel.getInlineElementsInRange(range.startOffset, range.endOffset)
    return InlayAssert(myFixture.file, inlays)
  }

  protected fun getLineRangeStartingWith(text: String): TextRange {
    val document = myFixture.editor.document
    val startOffset = document.charsSequence.indexOf(text)
    val lineNumber = document.getLineNumber(startOffset)
    return DocumentUtil.getLineTextRange(document, lineNumber)
  }

  protected fun configureFile(fileName: String, text: String) {
    myFixture.configureByText(fileName, text)
    myFixture.doHighlighting()
  }

}

class InlayAssert(private val file: PsiFile, val inlays: List<Inlay>) {

  fun assertNoInlays() {
    assertThat(inlays).hasSize(0)
  }

  fun assertInlays(vararg expectedInlays: String) {
    assertThat(expectedInlays.size).isNotEqualTo(0)

    val hintManager = ParameterHintsPresentationManager.getInstance()
    val hints = inlays.filter { hintManager.isParameterHint(it) }.map { it.offset to hintManager.getHintText(it) }
    val hintOffsets = hints.map { it.first }
    val hintNames = hints.map { it.second }

    val elements = hintOffsets.mapNotNull { file.findElementAt(it) }
    assertThat(hints.size).isEqualTo(expectedInlays.size).withFailMessage("Element at offsets: ${elements.joinToString(", ")}")

    val expect = expectedInlays.map { it.substringBefore("->") to it.substringAfter("->") }
    val expectedHintNames = expect.map { it.first }
    val expectedWordsAfter = expect.map { it.second }

    assertThat(hintNames).isEqualTo(expectedHintNames)

    val wordsAfter = elements.map { it.text }
    assertThat(wordsAfter).isEqualTo(expectedWordsAfter)
  }

}