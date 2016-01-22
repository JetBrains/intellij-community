package com.intellij.codeInsight.daemon.quickFix
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class CreateConstantFieldFromUsageTest extends LightCodeInsightFixtureTestCase {

  public void "test add import when there is a single type variant"() {
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
  
  public void "test inside annotation argument with braces"() {
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

  public void "test inside annotation argument no braces"() {
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

  public void "test insert presentable name when showing type lookup"() {
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
