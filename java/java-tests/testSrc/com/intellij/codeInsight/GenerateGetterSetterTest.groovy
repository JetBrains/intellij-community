/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.GenerateGetterHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.Nullable
/**
 * @author peter
 */
class GenerateGetterSetterTest extends LightCodeInsightFixtureTestCase {

  public void "test don't strip is of non-boolean fields"() {
    myFixture.addClass('class YesNoRAMField {}')
    myFixture.configureByText 'a.java', '''
class Foo {
    YesNoRAMField isStateForceMailField;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    YesNoRAMField isStateForceMailField;

    public YesNoRAMField getIsStateForceMailField() {
        return isStateForceMailField;
    }}
'''
  }
  
  public void "test strip is of boolean fields"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    boolean isStateForceMailField;

    <caret>
}
'''
    generateGetter()
    myFixture.checkResult '''
class Foo {
    boolean isStateForceMailField;

    public boolean isStateForceMailField() {
        return isStateForceMailField;
    }}
'''
  }

  private void generateGetter() {
    new GenerateGetterHandler() {
      @Override
      protected ClassMember[] chooseMembers(
        ClassMember[] members,
        boolean allowEmptySelection,
        boolean copyJavadocCheckbox,
        Project project,
        @Nullable @Nullable Editor editor) {
        return members
      }
    }.invoke(project, myFixture.editor, myFixture.file)
  }
}
