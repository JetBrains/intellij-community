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
package com.intellij.psi.codeStyle

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.assertj.core.api.Assertions.assertThat


class IndentOptionsCacheDropTest: LightCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    val instance = DetectableIndentOptionsProvider.getInstance()
    instance?.setEnabledInTest(true)
  }

  override fun tearDown() {
    val instance = DetectableIndentOptionsProvider.getInstance()
    instance?.setEnabledInTest(false)
    super.tearDown()
  }

  val code = """
class Test {
  public void test() {<caret>
    int a = 2;
  }
}
"""
  
  fun testDropIndentOptions_WhenTabSizeChanged() {
    val current = CodeStyleSettingsManager.getInstance(project).currentSettings
    val options = current.getCommonSettings(JavaLanguage.INSTANCE).indentOptions!!
    myFixture.configureByText(JavaFileType.INSTANCE, code)

    val tabSize = myFixture.editor.settings.getTabSize(project)
    assertThat(tabSize).isEqualTo(options.TAB_SIZE)
    
    options.TAB_SIZE = 14
    
    reinitEditorSettings()
    
    val newTabSize = myFixture.editor.settings.getTabSize(project)
    assertThat(newTabSize).isEqualTo(14)
  }

  fun testIndentOptionsCache_NotDroppedOnReinit() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(file)!!

    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(file)).isTrue()
  }
  
  fun testIndentOptionsCache_NotDroppedOnChange() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(file)!!

    myFixture.type(" abracadabra")
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(file)).isTrue()
  }
  
  fun testIndentOptionsDrop_OnDocumentChangeAndReinit() {
    myFixture.configureByText(JavaFileType.INSTANCE, code)
    val before: IndentOptions = IndentOptions.retrieveFromAssociatedDocument(file)!!

    myFixture.type(" abracadabra")
    
    reinitEditorSettings()
    
    assertThat(before === IndentOptions.retrieveFromAssociatedDocument(file)).isFalse()
  }
  
  private fun reinitEditorSettings() = (myFixture.editor as EditorImpl).reinitSettings()

}