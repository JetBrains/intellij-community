// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention


import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class AddDefaultConstructorFixTest extends LightJavaCodeInsightFixtureTestCase {

  void "test adding constructor to super class"() {
    def superClass = myFixture.addClass('abstract class Foo { Foo(int a) {} }')
    myFixture.configureByText 'a.java', 'class Bar extends Foo { <caret>Bar() { } }'
    def madeWritable = CodeInsightTestFixtureImpl.withReadOnlyFile(superClass.containingFile.virtualFile, project) {
      myFixture.launchAction(myFixture.findSingleIntention('Add package-private no-args constructor'))
    }
    assert superClass.constructors.size() == 2
    assert madeWritable
  }
}
