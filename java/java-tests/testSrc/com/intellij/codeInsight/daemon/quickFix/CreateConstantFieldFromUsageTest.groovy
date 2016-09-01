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
package com.intellij.codeInsight.daemon.quickFix
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class CreateConstantFieldFromUsageTest extends LightCodeInsightFixtureTestCase {

  void "test add import when there is a single type variant"() {
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    myFixture.addClass "package foo; public class Foo { public void someMethod() {} }"
    myFixture.configureByText "a.java", '''
class Test {
  void foo() { <caret>CONST.someMethod(); }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''import foo.Foo;

class Test {
    private static final <selection>Foo</selection> CONST = ;

    void foo() { CONST.someMethod(); }
}
'''
    assert !myFixture.lookup
  }

  void "test inside annotation argument with braces"() {
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    myFixture.configureByText "a.java", '''
interface A {}
@SuppressWarnings({A.CON<caret>ST})
class Test {}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''
interface A {
    <selection>String</selection> CONST = ;
}
@SuppressWarnings({A.CONST})
class Test {}
'''
  }

  void "test inside annotation argument no braces"() {
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    myFixture.configureByText "a.java", '''
interface A {}
@SuppressWarnings(A.CON<caret>ST)
class Test {}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''
interface A {
    <selection>String</selection> CONST = ;
}
@SuppressWarnings(A.CONST)
class Test {}
'''
  }

  void "test insert presentable name when showing type lookup"() {
    TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)
    myFixture.addClass "package foo; public class Foo { public void someMethod() {} }"
    myFixture.addClass "package bar; public class Bar { public void someMethod() {} }"
    myFixture.configureByText "a.java", '''
class Test {
  void foo() { <caret>CONST.someMethod(); }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''
class Test {
    private static final <selection>Bar</selection> CONST = ;

    void foo() { CONST.someMethod(); }
}
'''
    assert myFixture.lookup
    myFixture.type('\n')
    myFixture.checkResult '''import bar.Bar;

class Test {
    private static final Bar CONST = <caret>;

    void foo() { CONST.someMethod(); }
}
'''
  }
  
}
