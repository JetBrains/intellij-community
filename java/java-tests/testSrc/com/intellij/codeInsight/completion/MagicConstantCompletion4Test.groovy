/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.MagicConstant

/**
 * @author peter
 */
class MagicConstantCompletion4Test extends LightCodeInsightFixtureTestCase {
  LightProjectDescriptor projectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.mockJdk14)
    }
  }

  void "test suppress class constants in MagicConstant presence"() {
    assert !myFixture.javaFacade.findClass(MagicConstant.name, GlobalSearchScope.allScope(myFixture.project))

    myFixture.configureByText "a.java", """import java.util.*;
class Bar {
  static void foo(Calendar c) {
    c.setFirstDayOfWeek(<caret>)
  }
}
"""
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'FRIDAY', 'MONDAY'
    assert !(myFixture.lookupElementStrings.contains('JANUARY'))
    assert !(myFixture.lookupElementStrings.contains('Calendar.JANUARY'))
  }

}
