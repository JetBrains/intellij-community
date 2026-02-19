// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class GlobalMemberNameCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  @NeedsIndex.Full
  public void testMethodName() {
    myFixture.addClass("""
      package foo;
      
      public class Foo {
        public static int abcmethod() {}
        static void methodThatsNotVisible() {}
      }""");

    doTest("class Bar {{ abcm<caret> }}", true,
           """
             import static foo.Foo.abcmethod;
             
             class Bar {{ abcmethod()<caret> }}""");
  }

  @NeedsIndex.Full
  public void testFieldName() {
    myFixture.addClass("""
                         
                         package foo;
                         
                         public class Foo {
                           public static int abcfield = 2;
                           static final int fieldThatsNotVisible = 3;
                         }
                         """);

    doTest("class Bar {{ abcf<caret> }}", true,
           """
             import static foo.Foo.abcfield;

             class Bar {{ abcfield<caret> }}""");
  }

  @NeedsIndex.Full
  public void testFieldNameQualified() {
    myFixture.addClass("""
                         package foo;
                         
                         public class Foo {
                           public static int abcfield = 2;
                           static final int fieldThatsNotVisible = 3;
                         }
                         """);

    doTest("class Bar {{ abcf<caret> }}", false,
           """
             import foo.Foo;

             class Bar {{ Foo.abcfield<caret> }}""");
  }

  @NeedsIndex.Full
  public void testFieldNamePresentation() {
    myFixture.addClass("""
                         package foo;
                         
                         public class Foo {
                           public static int abcfield = 2;
                           static final int fieldThatsNotVisible = 3;
                         }
                         """);
    myFixture.configureByText("a.java", "class Bar {{ abcf<caret> }}");
    LookupElement element = complete()[0];
    LookupElementPresentation presentation = NormalCompletionTestCase.renderElement(element);
    assertEquals("Foo.abcfield", presentation.getItemText());
    assertEquals(" (foo)", presentation.getTailText());
    assertEquals("int", presentation.getTypeText());
  }

  private LookupElement[] complete() {
    return myFixture.complete(CompletionType.BASIC, 2);
  }

  @NeedsIndex.Full
  public void testQualifiedMethodName() {
    myFixture.addClass("""

                         package foo;

                         public class Foo {
                           public static int abcmethod() {}
                         }
                         """);

    doTest("class Bar {{ abcm<caret> }}", false, """
      import foo.Foo;

      class Bar {{ Foo.abcmethod()<caret> }}""");
  }

  @NeedsIndex.Full
  public void testIfThereAreAlreadyStaticImportsWithThatClass() {
    myFixture.addClass("""

                         package foo;

                         public class Foo {
                           public static int anotherMethod(int a) { }

                           public static int abcmethod() { }

                           void methodThatsNotVisible() { }
                         }
                         """);

    doTest("""
             import static foo.Foo.abcmethod;
             
             class Bar {{ abcmethod(); anoMe<caret> }}""", false,
              """
              import static foo.Foo.abcmethod;
              import static foo.Foo.anotherMethod;
              
              class Bar {{ abcmethod(); anotherMethod(<caret>) }}""");
  }

  @NeedsIndex.Full
  public void testExcludeClassFromCompletion() {
    myFixture.addClass("""
                         
                         package foo;
                         public class Foo {
                           public static int abcmethod() {}
                         }""");
    myFixture.addClass("""
                         package foo;
                         public class Excl {
                           public static int abcmethod2() { }
                         }""");

    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "*Excl");

    doTest("class Bar {{ abcm<caret> }}", true, """
      import static foo.Foo.abcmethod;

      class Bar {{ abcmethod()<caret> }}""");
  }

  @NeedsIndex.Full
  public void testExcludeMethodFromCompletion() {
    myFixture.addClass(
"""
        package foo;
              public class Foo {
                public static int abcmethod1() {}
                public static int abcmethodExcluded() {}
              }
        """);

    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo.Foo.abcmethodExcluded");

    doTest("class Bar {{ abcm<caret> }}", true, """
      import static foo.Foo.abcmethod1;

      class Bar {{ abcmethod1()<caret> }}""");
  }

  @NeedsIndex.Full
  public void testMergeOverloads() {
    myFixture.addClass(
    """
      package foo;
      public class Foo {
        public static int abcmethod(int a) {}
        public static int abcmethod(boolean a) {}
        public static int abcmethod1(boolean a) {}
      }
      """);

    myFixture.configureByText("a.java", "class Bar {{ abcm<caret> }}");
    LookupElement element = complete()[0];

    List<LookupElementPresentation.TextFragment> tail = NormalCompletionTestCase.renderElement(element).getTailFragments();
    assertEquals("(...)", tail.get(0).text);
    assertFalse(tail.get(0).isGrayed());
    assertEquals(" (foo)", tail.get(1).text);
    assertTrue(tail.get(1).isGrayed());
    UsefulTestCase.assertOrderedEquals(myFixture.getLookupElementStrings(), "abcmethod", "abcmethod1");
  }

  public void testMethodFromTheSameClass() {
    myFixture.configureByText("a.java",
  """
      class A {
        static void foo() {}
      
        static void goo() {
          f<caret>
        }
      }
      """);
    LookupElement element = complete()[0];
    LookupElementPresentation presentation = NormalCompletionTestCase.renderElement(element);
    assert "foo".equals(presentation.getItemText());
    myFixture.type("\n");
    myFixture.checkResult(
    """
    class A {
      static void foo() {}
    
      static void goo() {
        foo();<caret>
      }
    }
    """);
  }

  @NeedsIndex.Full
  public void testStaticImportBeforeAnIdentifier() {
    myFixture.addClass(
      """
      package test.t1;
      
      public enum DemoEnum
      {
              XXONE,
              TWO
      }
      """);
    doTest(
      """
             import test.t1.DemoEnum;
             
             public class Demo {
             
                     public static void doStuff(DemoEnum enumValue, String value) {}
                     public static void main(String[] args)
                     {
                             String val = "anyValue";
                             doStuff(XXON<caret>val);
                     }
             }
             """, true,
           """
             import test.t1.DemoEnum;
             
             import static test.t1.DemoEnum.XXONE;
             
             public class Demo {
             
                     public static void doStuff(DemoEnum enumValue, String value) {}
                     public static void main(String[] args)
                     {
                             String val = "anyValue";
                             doStuff(XXONE<caret>val);
                     }
             }
             """);
  }

  private void doTest(String input, boolean importStatic, String output) {
    myFixture.configureByText("a.java", input);

    LookupElement item = UsefulTestCase.assertOneElement(complete());
    if (importStatic) {
      ClassConditionKey<StaticallyImportable> key = ClassConditionKey.create(StaticallyImportable.class);
      item.as(key).setShouldBeImported(true);
    }

    myFixture.type("\n");
    myFixture.checkResult(output);
  }

  public void testNoResultsInIncompleteStaticImport() {
    String text = """
      package p;
      
      import static Foo.ba<caret>
      
      class Foo {
        static void bar() {}
      }
      """;
    PsiFile file = myFixture.addFileToProject("p/Foo.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.completeBasic();
    myFixture.checkResult(text);
  }

  @NeedsIndex.Full
  public void testNoGlobalReformatting() {
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_MULTILINE_BINARY_OPERATION = true;

    myFixture.addClass("package foo; public interface Foo { int XCONST = 42; }");
    PsiFile file = myFixture.addFileToProject("a.java", """
      class Zoo {
          void zoo(int i) {
              if (i == XCONS<caret> ||
                  CONTAINERS.contains(myElementType)) {
              }
          }
      }
    """);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type("\n");
    myFixture.checkResult("""
                            import foo.Foo;
                            
                            class Zoo {
                                  void zoo(int i) {
                                      if (i == Foo.XCONST<caret> ||
                                          CONTAINERS.contains(myElementType)) {
                                      }
                                  }
                              }
                            """);
  }
}
