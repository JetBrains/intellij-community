// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation

import com.intellij.codeInsight.navigation.MethodUpDownUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author peter
 */
class JavaMemberNavigationTest extends LightCodeInsightFixtureTestCase {
  
  void "test include anonymous and local classes"() {
    def file = myFixture.configureByText('a.java', '''
class Foo {
  void bar() {
    new Runnable() {
      void run() {}
    };
    class Local {
      void localMethod() {}
    }
  }
}
''')
    def offsets = MethodUpDownUtil.getNavigationOffsets(file, 0)
    assert file.text.indexOf('run') in offsets
    assert file.text.indexOf('Local') in offsets
    assert file.text.indexOf('localMethod') in offsets
  }
}
