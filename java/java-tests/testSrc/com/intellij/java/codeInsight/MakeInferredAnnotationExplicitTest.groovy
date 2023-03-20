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
package com.intellij.java.codeInsight

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.ArrayUtil
class MakeInferredAnnotationExplicitTest extends LightJavaCodeInsightFixtureTestCase {

  void "test contract and notNull"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    static String f<caret>oo() {
        return "s";
    }
}
'''
    def intention = myFixture.findSingleIntention("Insert '@Contract(pure = true) @NotNull'")
    assertTrue(intention.startInWriteAction())
    myFixture.launchAction(intention)
    myFixture.checkResult '''import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

class Foo {
    @NotNull
    @Contract(pure = true)
    static String f<caret>oo() {
        return "s";
    }
}
'''

  }

  void "test parameter"() {
    myFixture.configureByText 'a.java', '''
class Foo {
    static String foo(String s<caret>tr) {
        return str.trim();
    }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@NotNull'"))
    myFixture.checkResult '''import org.jetbrains.annotations.NotNull;

class Foo {
    static String foo(@NotNull String str) {
        return str.trim();
    }
}
'''

  }

  void "test custom notNull"() {
    myFixture.addClass("package foo; public @interface MyNotNull {}")
    NullableNotNullManager.getInstance(project).notNulls = ['foo.MyNotNull']
    NullableNotNullManager.getInstance(project).defaultNotNull = 'foo.MyNotNull'
    
    myFixture.configureByText 'a.java', '''
class Foo {
    static String f<caret>oo() {
        unknown();
        return "s";
    }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"))
    myFixture.checkResult '''import foo.MyNotNull;

class Foo {
    @MyNotNull
    static String f<caret>oo() {
        unknown();
        return "s";
    }
}
'''
  }
  
  void "test type use"() {
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}")
    NullableNotNullManager.getInstance(project).notNulls = ['foo.MyNotNull']
    NullableNotNullManager.getInstance(project).defaultNotNull = 'foo.MyNotNull'
    myFixture.configureByText 'a.java', '''
class Foo {
    static void foo(String[] ar<caret>ray) {
      System.out.println(array.length);
    }
}
'''
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"))
    myFixture.checkResult '''import foo.MyNotNull;

class Foo {
    static void foo(String @MyNotNull [] array) {
      System.out.println(array.length);
    }
}
'''
  }
  
  void "test type use qualified type"() {
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}")
    NullableNotNullManager.getInstance(project).notNulls = ['foo.MyNotNull']
    NullableNotNullManager.getInstance(project).defaultNotNull = 'foo.MyNotNull'
    myFixture.configureByText 'a.java', '''
import org.jetbrains.annotations.Contract;
import foo.MyNotNull;

class Sample {
    class Inner {
        String a;

        public Inner(String a) {
            this.a = a;
        }
    }

    class Usage {
        @Contract(value = " -> new", pure = true)
        private Sample.@MyNotNull Inner f<caret>oo() {
            String a = "a";
           return new Inner(a);
        }
    }
}'''
    assert myFixture.filterAvailableIntentions("Insert '@MyNotNull'").isEmpty()
  }
  
  @Override
  protected void tearDown() throws Exception {
    NullableNotNullManager.getInstance(project).notNulls = ArrayUtil.EMPTY_STRING_ARRAY
    NullableNotNullManager.getInstance(project).defaultNotNull = AnnotationUtil.NOT_NULL

    super.tearDown()
  }
}
