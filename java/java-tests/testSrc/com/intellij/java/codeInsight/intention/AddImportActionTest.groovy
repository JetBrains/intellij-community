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
package com.intellij.java.codeInsight.intention

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix
import com.intellij.lang.java.JavaLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection

class AddImportActionTest extends LightCodeInsightFixtureTestCase {
  private CodeStyleSettings settings

  void testMap15() {
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

  void testMapLatestLanguageLevel() {
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

  void testStringValue() {
    myFixture.addClass 'package java.lang; class StringValue {}'
    myFixture.addClass 'package foo; public class StringValue {}'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    String<caret>Value sv;
}
'''
    importClass()
    myFixture.checkResult '''import foo.StringValue;

public class Foo {
    String<caret>Value sv;
}
'''
  }

  void testPackageLocalInner() {
    myFixture.addClass 'package foo; class Outer { static class Inner {static String FOO = "";}}'

    myFixture.configureByText 'a.java', '''\
package foo;
class Usage {
  {
    String foo = In<caret>ner.FOO;
  }
}
'''

    importClass()

    myFixture.checkResult '''package foo;
class Usage {
  {
    String foo = Outer.Inner.FOO;
  }
}
'''
  }

  void testWrongTypeParams() throws Exception {
    myFixture.addClass 'package f; public class Foo {}'
    myFixture.configureByText 'a.java', '''\
public class Bar {
  Fo<caret>o<String> foo;
}
'''
    importClass()
    myFixture.checkResult '''\
import f.Foo;

public class Bar {
  Foo<String> foo;
}
'''
  }

  void testUseContext() {
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

  void "test use initializer"() {
    myFixture.addClass 'package foo; public class Map {}'
    myFixture.configureByText 'a.java', '''\
import java.util.HashMap;

public class Foo {
    Ma<caret>p l = new HashMap<>();
}
'''
    ImportClassFix intention = myFixture.findSingleIntention("Import class").delegate as ImportClassFix
    assert intention.classesToImport.collect { it.qualifiedName } == ['java.util.Map']
  }

  void testUseOverrideContext() {
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

  void testImportFoldingWithConflicts() {

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
  
  void testImportFoldingWithConflictsToJavaLang() {

    myFixture.addClass 'package p1; public class String {}'
    myFixture.addClass 'package p1; public class A1 {}'
    myFixture.addClass 'package p1; public class A2 {}'
    myFixture.addClass 'package p1; public class A3 {}'
    myFixture.addClass 'package p1; public class A4 {}'
    myFixture.addClass 'package p1; public class A5 {}'

    myFixture.configureByText 'C.java', '''package p2;

import p1.A1;
import p1.A2;
import p1.A3;
import p1.A4;

class C {

     A1 a1;
     A2 a2;
     A3 a3;
     A4 a4;
     A<caret>5 a5;

     String myName;
}

'''
    importClass()

    myFixture.checkResult '''package p2;

import p1.*;

import java.lang.String;

class C {

     A1 a1;
     A2 a2;
     A3 a3;
     A4 a4;
     A5 a5;

     String myName;
}

'''
  }

  void testAnnotatedImport() {
    myFixture.addClass '''
      import java.lang.annotation.*;
      @Target(ElementType.TYPE_USE) @interface TA { }'''.stripIndent().trim()

    myFixture.configureByText 'a.java', '''
      class Test {
          @TA
          public <caret>Collection<@TA String> c;
      }'''.stripIndent().trim()

    importClass()

    myFixture.checkResult '''
      import java.util.Collection;

      class Test {
          @TA
          public <caret>Collection<@TA String> c;
      }'''.stripIndent().trim()
  }

  void testAnnotatedQualifiedImport() {
    myFixture.addClass '''
      import java.lang.annotation.*;
      @Target(ElementType.TYPE_USE) @interface TA { }'''.stripIndent().trim()

    myFixture.configureByText 'a.java', '''
      class Test {
          java.u<caret>til.@TA Collection<@TA String> c;
      }'''.stripIndent().trim()

    reimportClass()

    myFixture.checkResult '''
      import java.util.Collection;

      class Test {
          <caret>@TA Collection<@TA String> c;
      }'''.stripIndent().trim()
  }

  void testUnresolvedAnnotatedImport() {
    myFixture.configureByText 'a.java', '''
      class Test {
          @Nullable Collection<caret> c;
      }'''.stripIndent().trim()

    importClass()

    myFixture.checkResult '''
      import java.util.Collection;

      class Test {
          @Nullable
          Collection<caret> c;
      }'''.stripIndent().trim()
  }

  void "test import class in class reference expression"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llection.class);
    }
}
'''
    importClass()
    myFixture.checkResult '''import java.util.Collection;

class Test {
    {
      equals(Co<caret>llection.class);
    }
}
'''
  }

  void "test import class in qualifier expression"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llections.emptySet());
    }
}
'''
    importClass()
    myFixture.checkResult '''import java.util.Collections;

class Test {
    {
      equals(Co<caret>llections.emptySet());
    }
}
'''
  }

  void "test don't import class in method call argument"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      equals(Co<caret>llection);
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  void "test don't import class if qualified name is not valid"() {
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
    importClass()
    myFixture.checkResult '''\
import a.MMM;

class Test {
    {
      MMM m;
    }
}
'''
  }

  void "test don't import class in assignment"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      Co<caret>llection = 2;
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  void "test don't import class in qualified reference at reference name"() {
    myFixture.configureByText 'a.java', '''
class Test {
    {
      Test.Te<caret>st
    }
}
'''
    assert !myFixture.filterAvailableIntentions("Import class")
  }

  void "test don't import class in qualified reference at foreign place"() {
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

  void "test allow to add import from javadoc"() {
    myFixture.configureByText 'a.java', '''
class Test {

  /**
   * {@link java.uti<caret>l.Map}
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

  void "test do not add import for default package"() {
    myFixture.configureByText 'a.java', '''
class Test {

  /**
   * {@link java.l<caret>ang.Math}
   */
  void run() {
  }
}
'''
    myFixture.enableInspections(new UnnecessaryFullyQualifiedNameInspection())
    myFixture.launchAction(myFixture.findSingleIntention("Remove unnecessary qualification"))
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

  void "test do not allow to add import in package-info file"() {
    myFixture.configureByText 'package-info.java', '''

/**
 * {@link java.lang.Ma<caret>th}
 */
package com.rocket.test;
'''
    assert myFixture.filterAvailableIntentions('Replace qualified name').isEmpty()
  }


  void "test keep methods formatting on add import"() {
    settings.getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true

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
  void setUp() throws Exception {
    super.setUp()
    settings = new CodeStyleSettings()
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    javaSettings.CLASS_NAMES_IN_JAVADOC = JavaCodeStyleSettings.SHORTEN_NAMES_ALWAYS_AND_ADD_IMPORT
    CodeStyleSettingsManager.getInstance(myFixture.project).setTemporarySettings(settings)
  }

  @Override
  void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(myFixture.project).dropTemporarySettings()
    settings = null
    super.tearDown()
  }

  private def importClass() {
    myFixture.launchAction(myFixture.findSingleIntention("Import class"))
  }

  private def reimportClass() {
    myFixture.enableInspections(new UnnecessaryFullyQualifiedNameInspection())
    myFixture.launchAction(myFixture.findSingleIntention("Replace qualified name with import"))
  }

  void "test disprefer deprecated classes"() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; @Deprecated public class Log {}'
    myFixture.configureByText 'a.java', '''\
public class Foo {
    Lo<caret>g l;
}
'''
    importClass()
    myFixture.checkResult '''import foo.Log;

public class Foo {
    Lo<caret>g l;
}
'''

  }

  void "test prefer from imported package"() {
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package foo; public class Imported {}'
    myFixture.addClass 'package bar; public class Log {}'
    myFixture.configureByText 'a.java', '''import foo.Imported;
public class Foo {
    Lo<caret>g l;
    Imported i;
}
'''
    importClass()
    myFixture.checkResult '''\
import foo.Imported;
import foo.Log;

public class Foo {
    Lo<caret>g l;
    Imported i;
}
'''
  }

  void "test remember chosen variants"() {
    ((StatisticsManagerImpl)StatisticsManager.getInstance()).enableStatistics(myFixture.getTestRootDisposable())
    myFixture.addClass 'package foo; public class Log {}'
    myFixture.addClass 'package bar; public class Log {}'

    def textBefore = '''\

public class Foo {
    Lo<caret>g l;
}
'''
    def textAfter = '''import bar.Log;

public class Foo {
    Lo<caret>g l;
}
'''
    myFixture.configureByText 'a.java', textBefore
    importClass()
    myFixture.checkResult textAfter

    myFixture.addClass("package aPackage; public class Log {}")
    myFixture.configureByText 'b.java', textBefore
    importClass()
    myFixture.checkResult textAfter
  }
}
