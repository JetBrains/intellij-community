// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

import static com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl.seemsScrambledByStructure
@CompileStatic
class SeemsScrambledTest extends LightJavaCodeInsightFixtureTestCase {

  void "test Id annotation"() {
    assert !seemsScrambledByStructure(myFixture.addClass('public @interface Id {}'))
  }

  void "test inner enum"() {
    assert !seemsScrambledByStructure(myFixture.addClass('public class Foo { enum v1 {} }').innerClasses[0])
  }

  void "test scrambled"() {
    assert seemsScrambledByStructure(myFixture.addClass('public class a { void b() {} }'))
  }

  void "test has non-scrambled method"() {
    assert !seemsScrambledByStructure(myFixture.addClass('public class a { void doSomething() {} }'))
  }

}
