// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.structuralsearch

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.structuralsearch.PredefinedConfigurationUtil
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialogKeys
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class TemplatesCompletionTest : LightJavaCodeInsightFixtureTestCase() {
  fun prepare(text: String) {
    myFixture.configureByText(JavaFileType.INSTANCE, text)
    myFixture.editor.putUserData(StructuralSearchDialogKeys.TEST_STRUCTURAL_SEARCH_DIALOG, true)
  }

  private val renderedLookupElementTexts: Collection<Pair<String, String>>
    get() = myFixture.completeBasic()
      .map { LookupElementPresentation.renderElement(it) }
      .map { "${it.itemText}" to "${it.typeText}" }

  fun testCompletion() {
    prepare("all fields of<caret>")
    val customTemplate = PredefinedConfigurationUtil.createConfiguration("all fields of a class bis", "",
                                                                         "", "", JavaFileType.INSTANCE)
    customTemplate.isPredefined = false
    ConfigurationManager.getInstance(project).addConfiguration(customTemplate)
    val elements = renderedLookupElementTexts
    assert("All fields of a class" to "Java search template" in elements)                   // Predefined legacy Java configuration
    assert("all fields of a class bis" to "Java search template, user defined" in elements) // Newly created configuration
  }
}