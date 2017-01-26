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
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import java.util.regex.Pattern


abstract class InlayParameterHintsTest : LightCodeInsightFixtureTestCase() {
  
  private var isParamHintsEnabledBefore = false
  private lateinit var stateBefore: Element

  companion object {
    val pattern: Pattern = Pattern.compile("<hint\\s+text=\"([0-9a-zA-Z]+)\"/>")
  }
  
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
  
  fun checkInlays(fileName: String, text: String) {
    myFixture.configureByText(fileName, text)

    val document = myFixture.getDocument(myFixture.file)
    val expectedInlays = extractInlays(document)
    val actualInlays = getActualInlays()

    assertThat(actualInlays.size)
      .withFailMessage("Expected ${expectedInlays.size} elements with hints, Actual elements count ${actualInlays.size}" +
                       ", file text: \n\n ${file.text} \n\n isCommitted ${isCommitted(file)} \n\n" +
                       "All inlays: ${actualInlays}")
      .isEqualTo(expectedInlays.size)

    actualInlays.zip(expectedInlays).forEach {
      assertThat(it.first).isEqualTo(it.second)
    }
  }

  private fun isCommitted(file: PsiFile): Boolean {
    val manager = PsiDocumentManager.getInstance(file.project)
    val document = manager.getDocument(file)

    assertThat(document).isNotNull()
    return manager.isCommitted(document!!)
  }

  private fun getActualInlays(): List<InlayInfo> {
    myFixture.doHighlighting()
    val editor = myFixture.editor
    val allInlays = editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength)

    if (ParameterHintsPassFactory.isDebug()) {
      println("${System.nanoTime()}: [HintTests] inlays extracted")
    }
    
    val hintManager = ParameterHintsPresentationManager.getInstance()
    return allInlays
      .filter { hintManager.isParameterHint(it) }
      .map { InlayInfo(hintManager.getHintText(it), it.offset) }
      .sortedBy { it.offset }
  }

  fun extractInlays(document: Document): List<InlayInfo> {
    val text = document.text
    val matcher = pattern.matcher(text)

    val inlays = mutableListOf<InlayInfo>()
    var extractedLength = 0

    while (matcher.find()) {
      val start = matcher.start()
      val matchedLength = matcher.end() - start

      val realStartOffset = start - extractedLength
      inlays += InlayInfo(matcher.group(1), realStartOffset)

      removeText(document, realStartOffset, matchedLength)
      extractedLength += (matcher.end() - start)
    }

    return inlays
  }

  private fun removeText(document: Document, realStartOffset: Int, matchedLength: Int) {
    WriteCommandAction.runWriteCommandAction(myFixture.project, {
      document.replaceString(realStartOffset, realStartOffset + matchedLength, "")
    })
  }


}