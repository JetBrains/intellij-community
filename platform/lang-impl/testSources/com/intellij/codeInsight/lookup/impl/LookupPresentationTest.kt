// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.*
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class LookupPresentationTest : LightPlatformCodeInsightTestCase() {
  private val preferredElement = "element1"

  fun `test default ordering (most preferred are first)`() {
    testPreferredElementPosition(expectedPosition = 0)
  }

  fun `test customized ordering (most preferred are last)`() {
    project.messageBus.connect(testRootDisposable).subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, newLookup ->
      (newLookup as? LookupEx)?.presentation = LookupPresentation.Builder().withMostRelevantOnTop(false).build()
    })
    testPreferredElementPosition(expectedPosition = 2)
  }

  private fun testPreferredElementPosition(expectedPosition: Int) {
    configureFromFileText("test.txt", "")
    val elements = createLookupElements()
    val lookup = LookupManager.getInstance(project).showLookup(editor, *elements) as LookupImpl
    assertEquals("Incorrect selected lookup item index", expectedPosition, lookup.selectedIndex)
    val selectedElement = lookup.list.model.getElementAt(lookup.selectedIndex) as LookupElement
    assertEquals("Incorrect selected lookup item", preferredElement, selectedElement.lookupString)
    lookup.hide()
  }

  private fun createLookupElements(): Array<LookupElement> {
    return arrayOf(
      LookupElementBuilder.create(preferredElement),
      LookupElementBuilder.create("element2"),
      LookupElementBuilder.create("element3")
    )
  }
}