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
package com.intellij.codeInsight.intention

import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

public class AddImportActionTest extends LightCodeInsightFixtureTestCase {
  private CodeStyleSettings settings

  public void testMap15() {
    IdeaTestUtil.withLevel(myModule, LanguageLevel.JDK_1_5, {
      myFixture.configureByText 'a.java', '''\
public class Foo {
    void foo() {
        Ma<caret>p l;
    }
}
'''
      importClass()
      myFixture.checkResult '''import java.util.Map;

public class Foo {
    void foo() {
        Ma<caret>p l;
    }
}
'''
    })
  }

  public void testMapLatestLanguageLevel() {
    myFixture.configureByText 'a.java', '''\
public class Foo {
    void foo() {
        Ma<caret>p l;
    }
}
'''
    importClass()
    myFixture.checkResult '''import java.util.Map;

public class Foo {
    void foo() {
        Ma<caret>p l;
    }
}
'''
  }

  public void testStringValue() {
    myFixture.addClass 'package java.lang; class StringValue {}'
    myFixture.addClass 'package foo; public class StringValue {}'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    String<caret>Value sv;
}
'''
    importClass()
    myFixture.checkResult '''import foo.*;

public class Foo {
    String<caret>Value sv;
}
'''
  }

  public void testUseContext() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; public class Log {}'
    myFixture.addClass 'package bar; public class LogFactory { public static Log log(){} }'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
'''
    importClass()
    myFixture.checkResult '''import bar.Log;

public class Foo {
    Lo<caret>g l = bar.LogFactory.log();
}
'''
  }

  public void testUseOverrideContext() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; public class Log {}'
    myFixture.addClass 'package goo; public class Super { public void foo(foo.Log log) {} }'
    myFixture.configureByText 'a.java', '''\
public class Foo extends goo.Super {
    @Override
    public void foo(Log<caret> log) {}
}
'''
    importClass()
    myFixture.checkResult '''import foo.Log;

public class Foo extends goo.Super {
    @Override
    public void foo(Log<caret> log) {}
}
'''
  }

  public void testImportFoldingWithConflicts() {

    myFixture.addClass 'package p1; public class B {}'
    myFixture.addClass 'package p1; public class A1 {}'
    myFixture.addClass 'package p1; public class A2 {}'
    myFixture.addClass 'package p1; public class A3 {}'
    myFixture.addClass 'package p1; public class A4 {}'
    myFixture.addClass 'package p1; public class A5 {}'

    myFixture.addClass 'package p2; public class B {}'
    myFixture.configureByText 'C.java', '''package p2;

import p1.A1;
import p1.A2;
import p1.A3;
import p1.A4;
import p1.B;

class C {

     A1 a1;
     A2 a2;
     A3 a3;
     A4 a4;
     A<caret>5 a5;

     B b;
}

'''
    importClass()

    myFixture.checkResult '''package p2;

import p1.*;
import p1.B;

class C {

     A1 a1;
     A2 a2;
     A3 a3;
     A4 a4;
     A5 a5;

     B b;
}

'''
  }


  public void testAnnotatedImport() {
    myFixture.configureByText 'a.java', '''
import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA Collection<caret> c;
}
'''
    importClass();
    myFixture.checkResult '''
import java.lang.annotation.*;
import java.util.Collection;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA Collection<caret> c;
}
'''
  }

  public void testAnnotatedQualifiedImport() {
    myFixture.configureByText 'a.java', '''
import java.lang.annotation.*;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    java.util.@TA Collection<caret> c;
}
'''
    reimportClass();
    myFixture.checkResult '''
import java.lang.annotation.*;
import java.util.Collection;

@Target(ElementType.TYPE_USE) @interface TA { }

class Test {
    @TA Collection<caret> c;
}
'''
  }

  public void testUnresolvedAnnotatedImport() {
    myFixture.configureByText 'a.java', '''
class Test {
    @Nullable Collection<caret> c;
}
'''
    importClass();
    myFixture.checkResult '''import java.util.Collection;

class Test {
    @Nullable
    Collection<caret> c;
}
'''
  }

  public void "test import class in class reference expression"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llection.class);
    }
}
'''
    importClass();
    myFixture.checkResult '''import java.util.Collection;

class Test {
    {
      equals(Co<caret>llection.class);
    }
}
'''
  }

  public void "test import class in qualifier expression"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llections.emptySet());
    }
}
'''
    importClass();
    myFixture.checkResult '''import java.util.Collections;

class Test {
    {
      equals(Co<caret>llections.emptySet());
    }
}
'''
  }

  public void "test don't import class in method call argument"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llection);
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  public void "test don't import class if qualified name is not valid"() {
    myFixture.addClass('''
package a..p;
public class MMM {}
''')
    myFixture.configureByText 'a.java', '''
class Test {
    {
      MM<caret>M m;
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  public void "test don't import class in assignment"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      Co<caret>llection = 2;
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  public void "test don't import class in qualified reference at reference name"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      Test.Te<caret>st
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  public void "test don't import class in qualified reference at foreign place"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      String s = "";
      s.<caret>
      String p = "";
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

    public void "test allow to add import from javadoc"() {
    myFixture.configureByText 'a.java', '''
class Test {

  /**
   * {@link java.util.Ma<caret>p}
   */
  void run() {
  }
}
'''
    reimportClass()
    myFixture.checkResult '''\
import java.util.Map;

class Test {

  /**
   * {@link Map}
   */
  void run() {
  }
}
'''
  }

  public void "test do not add import for default package"() {
    myFixture.configureByText 'a.java', '''
class Test {

  /**
   * {@link java.lang.Ma<caret>th}
   */
  void run() {
  }
}
'''
    reimportClass()
    myFixture.checkResult '''
class Test {

  /**
   * {@link Math}
   */
  void run() {
  }
}
'''
  }

  public void "test do not allow to add import in package-info file"() {
    myFixture.configureByText 'package-info.java', '''

/**
 * {@link java.lang.Ma<caret>th}
 */
package com.rocket.test;
'''
    assert myFixture.filterAvailableIntentions('Replace qualified name').isEmpty()
  }


  public void "test keep methods formatting on add import"() {
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;

    myFixture.configureByText 'Tq.java', '''
class Tq {

    private Li<caret>st<String> test = null;

    private String varA = "AAA";
    private String varBLonger = "BBB";


    public String getA         () { return varA;       }

    public String getVarBLonger() { return varBLonger; }

}
'''
    importClass()
    myFixture.checkResult '''import java.util.List;

class Tq {

    private List<String> test = null;

    private String varA = "AAA";
    private String varBLonger = "BBB";


    public String getA         () { return varA;       }

    public String getVarBLonger() { return varBLonger; }

}
'''
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    settings = new CodeStyleSettings()
    CodeStyleSettingsManager.getInstance(myFixture.project).setTemporarySettings(settings);
  }

  @Override
  public void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(myFixture.project).dropTemporarySettings();
    settings = null
    super.tearDown();
  }

  private def importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"))
  }

  private def reimportClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Replace qualified name with 'import'"))
  }
}
