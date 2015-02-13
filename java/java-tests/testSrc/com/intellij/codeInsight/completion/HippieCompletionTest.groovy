/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
/**
 * @author peter
 */
class HippieCompletionTest extends LightCodeInsightFixtureTestCase {

  public void testDollars() {
    myFixture.configureByText "a.txt", '''
$some_long_variable_name = Obj::instance();
$some_lon<caret>
'''
    complete()
    myFixture.checkResult '''
$some_long_variable_name = Obj::instance();
$some_long_variable_name<caret>
'''
  }

  public void testFromAnotherFile() {
    myFixture.configureByText "b.txt", '''
$some_local = 1;
'''
    myFixture.configureByText "a.txt", '''
$some_long_variable_name = Obj::instance();
$some_lo<caret>
'''

    complete()
    myFixture.checkResult '''
$some_long_variable_name = Obj::instance();
$some_long_variable_name<caret>
'''
    complete()
    myFixture.checkResult '''
$some_long_variable_name = Obj::instance();
$some_local<caret>
'''
    backComplete()
    myFixture.checkResult '''
$some_long_variable_name = Obj::instance();
$some_long_variable_name<caret>
'''

  }

  public void testFromAnotherFile2() {
    myFixture.configureByText "b.txt", '''
foo function foo2
'''
    myFixture.configureByText "a.txt", '''
f<caret>
'''

    complete()
    myFixture.checkResult '''
foo2<caret>
'''
    complete()
    myFixture.checkResult '''
function<caret>
'''
    complete()
    myFixture.checkResult '''
foo<caret>
'''
    myFixture.configureByText "a.txt", '''
f<caret>
'''
    backComplete()
    myFixture.checkResult '''
foo<caret>
'''

    backComplete()
    myFixture.checkResult '''
function<caret>
'''
    backComplete()
    myFixture.checkResult '''
foo2<caret>
'''
  }

  public void "test no middle matching"() {
    myFixture.configureByText "a.txt", '''
fooExpression
exp<caret>
'''
    complete()
    myFixture.checkResult '''
fooExpression
exp<caret>
'''
  }

  public void "test words from javadoc"() {
    myFixture.configureByText "a.java", '''
/** some comment */
com<caret>
'''
    complete()
    myFixture.checkResult '''
/** some comment */
comment<caret>
'''
  }
  
  public void "test words from line comments"() {
    myFixture.configureByText "a.java", '''
// some comment2
com<caret>
'''
    complete()
    myFixture.checkResult '''
// some comment2
comment2<caret>
'''
  }
  public void "test words from block comments"() {
    myFixture.configureByText "a.java", '''
/* some comment3 */
com<caret>
'''
    complete()
    myFixture.checkResult '''
/* some comment3 */
comment3<caret>
'''
  }

  public void "test complete in string literal"() {
    myFixture.configureByText "a.java", '''
class Foo {
  public Collection<JetFile> allInScope(@NotNull GlobalSearchScope scope) {
    System.out.println("allInSco<caret>: " + scope);
  }
}
'''
    complete()
    myFixture.checkResult '''
class Foo {
  public Collection<JetFile> allInScope(@NotNull GlobalSearchScope scope) {
    System.out.println("allInScope<caret>: " + scope);
  }
}
'''
  }

  public void "test complete variable name in string literal"() {
    myFixture.configureByText "a.java", '''
class Xoo {
  String foobar = "foo<caret>";
}
'''
    complete()
    myFixture.checkResult '''
class Xoo {
  String foobar = "foobar<caret>";
}
'''

  }

  public void "test file start"() {
    myFixture.configureByText "a.java", '''<caret>
class Xoo {
}
'''
    complete()
    myFixture.checkResult '''class<caret>
class Xoo {
}
'''
  }

  public void "test cpp indirection"() {
    myFixture.configureByText "a.c", '''f<caret>
foo->bar
'''
    complete()
    myFixture.checkResult '''foo<caret>
foo->bar
'''
  }

  public void "test numbers"() {
    myFixture.configureByText "a.c", '''246<caret>
24601
'''
    complete()
    myFixture.checkResult '''24601<caret>
24601
'''
  }

  public void "test inside word"() {
    myFixture.configureByText "a.c", 'foo fox f<caret>bar'
    complete()
    myFixture.checkResult 'foo fox fox<caret>bar'
    complete()
    myFixture.checkResult 'foo fox foo<caret>bar'
  }

  public void "test multiple carets"() {
    myFixture.configureByText "a.txt", "fox food floor f<caret> f<caret>"
    complete()
    myFixture.checkResult "fox food floor floor<caret> floor<caret>"
    complete()
    myFixture.checkResult "fox food floor food<caret> food<caret>"
    complete()
    myFixture.checkResult "fox food floor fox<caret> fox<caret>"
  }

  public void "test multiple carets backward"() {
    myFixture.configureByText "a.txt", "f<caret> f<caret> fox food floor"
    backComplete()
    myFixture.checkResult "fox<caret> fox<caret> fox food floor"
    backComplete()
    myFixture.checkResult "food<caret> food<caret> fox food floor"
    backComplete()
    myFixture.checkResult "floor<caret> floor<caret> fox food floor"
  }

  private void complete() {
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_COMPLETION)
  }

  private void backComplete() {
    myFixture.performEditorAction(IdeActions.ACTION_HIPPIE_BACKWARD_COMPLETION)
  }
}
