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
package com.intellij.java.codeInsight.completion

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

import static com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl.seemsScrambledByStructure
/**
 * @author peter
 */
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
