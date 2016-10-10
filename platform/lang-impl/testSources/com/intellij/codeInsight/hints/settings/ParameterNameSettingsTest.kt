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

import junit.framework.TestCase

class ParameterNameSettingsTest : TestCase() {

  fun `test deleted value is saved to state`() {
    val settings = ParameterNameHintsSettings()
    val ignoreSet = settings.ignorePatternSet

    assert(ignoreSet.size > 0)
    val first = ignoreSet.first()
    ignoreSet.remove(first)

    settings.ignorePatternSet = ignoreSet

    val diff = settings.state!!.diff
    assert(diff.size == 1)
    assert(diff[0].startsWith("-"))
    assert(diff[0].endsWith(first))
  }

  fun `test saved value is saved to state`() {
    val settings = ParameterNameHintsSettings()
    val newPattern = "java.util.*(*)"
    settings.addIgnorePattern(newPattern)

    val diff = settings.state!!.diff
    assert(diff.size == 1)
    assert(diff[0].startsWith("+"))
    assert(diff[0].endsWith(newPattern))
  }

  fun `test on defaults change ensure removed default items remains removed`() {
    val settings = ParameterNameHintsSettings()
    settings.setDefaultSet(setOf("aaa", "bbb", "ccc"))
    
    settings.ignorePatternSet = setOf("aaa", "ccc", "zzz")
    settings.addIgnorePattern("xxx")
    
    val savedState = settings.state
    
    val newSettings = ParameterNameHintsSettings()
    newSettings.setDefaultSet(setOf("aaa", "bbb", "ccc", "qqq", "xxx"))
    newSettings.loadState(savedState)

    val newIgnoreSet = newSettings.ignorePatternSet
    assert(newIgnoreSet.containsAll(setOf("aaa", "ccc", "qqq", "zzz", "xxx")))
    assert(newIgnoreSet.size == 5)
    newSettings.ignorePatternSet = newSettings.ignorePatternSet
    
    val diff = newSettings.state!!.diff
    assert(diff.containsAll(setOf("-bbb", "+zzz")))
    assert(diff.size == 2)
  }
  
}