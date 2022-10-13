// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import groovy.transform.CompileStatic

@CompileStatic
class CreateConstantFieldFromUsageTest extends LightJavaCodeInsightFixtureTestCase {

  void "test add import when there is a single type variant"() {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
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
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
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
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
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
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    myFixture.addClass "package foo; public class Foo { public void someMethod() {} }"
    myFixture.addClass "package bar; public class Bar { public void someMethod() {} }"
    myFixture.configureByText "a.java", '''\
class Test {
  void foo() { <caret>CONST.someMethod(); }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''\
import bar.Bar;

class Test {
    private static final <selection>Bar</selection> CONST = ;

    void foo() { CONST.someMethod(); }
}
'''
    assert myFixture.lookup
    myFixture.type('\n')
    myFixture.checkResult '''\
import bar.Bar;

class Test {
    private static final Bar CONST = <caret>;

    void foo() { CONST.someMethod(); }
}
'''
  }

  void "test overload methods with single suggestion"() {
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
    myFixture.configureByText "a.java", '''
class Foo {}
class Test {
    {
        foo(new Foo(), <caret>BAR);
    }

    void foo(Foo f, String d) {}
    void foo(Foo f, String d, String d2) {}

}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Create constant field"))
    myFixture.checkResult '''
class Foo {}
class Test {
    private static final String BAR = ;

    {
        foo(new Foo(), BAR);
    }

    void foo(Foo f, String d) {}
    void foo(Foo f, String d, String d2) {}

}
'''
  }

  void "test no constant suggestion when name is not upper cased"() {
    myFixture.configureByText "a.java", '''
class Foo {
   {
     B<caret>ar
   }
}
'''
    assertEmpty myFixture.filterAvailableIntentions("Create constant field")
  }
}
