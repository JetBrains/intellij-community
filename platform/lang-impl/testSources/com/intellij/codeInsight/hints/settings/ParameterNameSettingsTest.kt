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
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiElement
import junit.framework.TestCase
import org.jdom.Element


class MockInlayProvider(override val defaultBlackList: Set<String>): InlayParameterHintsProvider {

  override fun getParameterHints(element: PsiElement) = emptyList<InlayInfo>()

  override fun getMethodInfo(element: PsiElement) = null
  
}


class ParameterNameSettingsTest : TestCase() {

  lateinit var settings: ParameterNameHintsSettings
  lateinit var inlayProvider: InlayParameterHintsProvider
  
  override fun setUp() {
    settings = ParameterNameHintsSettings()
    inlayProvider = MockInlayProvider(setOf())
  }
  
  fun defaultSettingsUpdated(vararg newDefault: String) {
    inlayProvider = MockInlayProvider(setOf(*newDefault))
  }

  fun addIgnorePattern(newPattern: String) {
    settings.addIgnorePattern(PlainTextLanguage.INSTANCE, newPattern)
  }

  fun setIgnorePattern(vararg newPatternSet: String) {
    val base = inlayProvider.defaultBlackList
    val diff = Diff.build(base, setOf(*newPatternSet))
    
    settings.setBlackListDiff(PlainTextLanguage.INSTANCE, diff)
  }
  
  fun getIgnoreSet(): Set<String> {
    val diff = settings.getBlackListDiff(PlainTextLanguage.INSTANCE)
    return diff.applyOn(inlayProvider.defaultBlackList)
  }

  fun `test ignore pattern is added`() {
    defaultSettingsUpdated("xxx")
    
    var ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 1)
    
    addIgnorePattern("aaa")
    
    ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 2)
    assert(ignoreSet.contains("aaa"))
    assert(ignoreSet.contains("xxx"))
  }

  fun `test if empty element is passed settings are dropped`() {
    addIgnorePattern("new_ignore_pattern")

    var ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 1)

    settings.loadState(Element("element"))

    ignoreSet = getIgnoreSet()
    assert(ignoreSet.isEmpty())
  }

  fun `test removed pattern is removed when defaults are updated`() {
    defaultSettingsUpdated("aaa", "bbb")

    var ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 2)

    setIgnorePattern("aaa")
    assert(getIgnoreSet().size == 1)
    
    defaultSettingsUpdated("aaa", "bbb", "ccc")
    
    ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 2)
    assert(ignoreSet.contains("aaa"))
    assert(ignoreSet.contains("ccc"))
  }

  fun `test added items remain added on defaults update`() {
    defaultSettingsUpdated("aaa")
    var ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 1)

    addIgnorePattern("xxx")
    ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 2)

    defaultSettingsUpdated("aaa", "bbb")
    ignoreSet = getIgnoreSet()
    assert(ignoreSet.size == 3)
    assert(ignoreSet.contains("aaa"))
    assert(ignoreSet.contains("bbb"))
    assert(ignoreSet.contains("xxx"))
  }
  
}