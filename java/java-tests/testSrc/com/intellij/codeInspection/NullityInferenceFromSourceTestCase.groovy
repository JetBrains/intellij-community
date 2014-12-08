/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.DfaUtil
import com.intellij.codeInspection.dataFlow.Nullness
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

import static com.intellij.codeInspection.dataFlow.Nullness.*
/**
 * @author peter
 */
abstract class NullityInferenceFromSourceTestCase extends LightCodeInsightFixtureTestCase {

  void "test return string literal"() {
    assert inferNullity(parse('String foo() { return "a"; }')) == NOT_NULL
  }

  void "test return null"() {
    assert inferNullity(parse('String foo() { return null; }')) == NULLABLE
  }

  void "test primitive return type"() {
    assert inferNullity(parse('int foo() { return "z"; }')) == UNKNOWN
  }

  void "test delegation"() {
    assert inferNullity(parse('String foo() { return bar(); }; String bar() { return "z"; }; ')) == NOT_NULL
  }

  void "test same delegate method invoked twice"() {
    assert inferNullity(parse('''
String foo() { 
  if (equals(2)) return bar();
  if (equals(3)) return bar();
  return "abc"; 
}
String bar() { return "z"; }
''')) == NOT_NULL
  }

  void "test unknown wins over a single delegate"() {
    assert inferNullity(parse('''
String foo() { 
  if (equals(3)) return bar();
  return smth; 
}
String bar() { return "z"; }
''')) == UNKNOWN
  }

  void "test if branch returns null"() {
    assert inferNullity(parse('String bar() { if (equals(2)) return null; return "a"; }; ')) == NULLABLE
  }

  void "test delegation to nullable means nothing"() {
    assert inferNullity(parse('String foo() { return bar("2"); }; String bar(String s) { if (s != "2") return null; return "a"; }; ')) == UNKNOWN
  }

  void "test return boxed boolean constant"() {
    assert inferNullity(parse('Object foo() { return true; }')) == NOT_NULL
  }

  void "test return boxed boolean value"() {
    assert inferNullity(parse('Object foo(Object o) { return o == null; }')) == NOT_NULL
  }

  void "test return boxed integer"() {
    assert inferNullity(parse('Object foo() { return 1; }')) == NOT_NULL
  }

  void "test null inside lambda"() {
    assert inferNullity(parse('Object foo() { return () -> { return null; }; }')) == NOT_NULL
  }

  protected abstract Nullness inferNullity(PsiMethod method)

  protected PsiMethod parse(String method) {
    return myFixture.addClass("final class Foo { $method }").methods[0]
  }

  static class LightInferenceTest extends NullityInferenceFromSourceTestCase {
    Nullness inferNullity(PsiMethod method) {
      return NullableNotNullManager.isNotNull(method) ? NOT_NULL : NullableNotNullManager.isNullable(method) ? NULLABLE : UNKNOWN
    }

    void "test skip when errors"() {
      assert inferNullity(parse('String foo() { if(); return 2; } ')) == UNKNOWN
    }
  }

  static class DfaInferenceTest extends NullityInferenceFromSourceTestCase {
    Nullness inferNullity(PsiMethod method) {
      return DfaUtil.inferMethodNullity(method)
    }
  }
}
