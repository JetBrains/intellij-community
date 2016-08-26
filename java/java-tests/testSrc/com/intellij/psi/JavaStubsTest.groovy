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
package com.intellij.psi

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer
import com.intellij.psi.impl.source.PsiClassImpl
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class JavaStubsTest extends LightCodeInsightFixtureTestCase {

  void "test resolve from annotation method default"() {
    def cls = myFixture.addClass("""
      public @interface BrokenAnnotation {
        enum Foo {DEFAULT, OTHER}
        Foo value() default Foo.DEFAULT;
      }
      """.stripIndent())

    def file = cls.containingFile as PsiFileImpl
    assert file.stub

    def ref = (cls.methods[0] as PsiAnnotationMethod).defaultValue
    assert file.stub

    assert ref instanceof PsiReferenceExpression
    assert ref.resolve() == cls.innerClasses[0].fields[0]
    assert file.stub
  }

  void "test literal annotation value"() {
    def cls = myFixture.addClass("""
      class Foo {
        @org.jetbrains.annotations.Contract(pure=true)
        native int foo();
      }
      """.stripIndent())

    def file = cls.containingFile as PsiFileImpl
    assert ControlFlowAnalyzer.isPure(cls.methods[0])
    assert file.stub
    assert !file.contentsLoaded
  }

  void "test local variable annotation doesn't cause stub-ast switch"() {
    def cls = myFixture.addClass("""
      class Foo {
        @Anno int foo() {
          @Anno int var = 2;
        }
      }
      @interface Anno {}
      """)

    def file = cls.containingFile as PsiFileImpl
    assert AnnotatedElementsSearch.searchPsiMethods(myFixture.findClass("Anno"), GlobalSearchScope.allScope(project)).size() == 1
    assert file.stub
    assert !file.contentsLoaded
  }

  void "test applying type annotations"() {
    def cls = myFixture.addClass("""
      import java.lang.annotation.*;
      class Foo {
        @Target(ElementType.TYPE_USE)
        @interface TA { String value(); }

        private @TA String f1;

        private static @TA int m1(@TA int p1) { return 0; }
      }
      """.stripIndent())

    def f1 = cls.fields[0].type
    def m1 = cls.methods[0].returnType
    def p1 = cls.methods[0].parameterList.parameters[0].type
    assert (cls as PsiClassImpl).stub

    assert f1.getCanonicalText(true) == "java.lang.@Foo.TA String"
    assert m1.getCanonicalText(true) == "@Foo.TA int"
    assert p1.getCanonicalText(true) == "@Foo.TA int"
  }
}