package com.intellij.java.codeInsight;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;

public class MakeInferredAnnotationExplicitTest extends LightJavaCodeInsightFixtureTestCase {
  public void test_contract_and_notNull() {
    myFixture.configureByText("a.java", """
      class Foo {
        static String f<caret>oo() {
          return "s";
        }
      }""");
    IntentionAction intention = myFixture.findSingleIntention("Insert '@Contract(pure = true) @NotNull'");
    assertTrue(intention.startInWriteAction());
    myFixture.launchAction(intention);
    myFixture.checkResult("""
      import org.jetbrains.annotations.Contract;
      import org.jetbrains.annotations.NotNull;

      class Foo {
        @Contract(pure = true)
        static @NotNull String f<caret>oo() {
          return "s";
        }
      }""");
  }

  public void test_parameter() {
    myFixture.configureByText("a.java", """
      class Foo {
        static String foo(String s<caret>tr) {
          return str.trim();
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@NotNull'"));
    myFixture.checkResult("""
      import org.jetbrains.annotations.NotNull;

      class Foo {
        static String foo(@NotNull String str) {
          return str.trim();
        }
      }""");
  }

  public void test_custom_notNull() {
    myFixture.addClass("package foo; public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(getProject()).setNotNulls("foo.MyNotNull");

    myFixture.configureByText("a.java", """
      class Foo {
        static String f<caret>oo() {
          unknown();
          return "s";
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"));
    myFixture.checkResult("""
                            import foo.MyNotNull;
                                  
                            class Foo {
                              @MyNotNull
                              static String f<caret>oo() {
                                unknown();
                                return "s";
                              }
                            }""");
  }

  public void test_type_use() {
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(getProject()).setNotNulls("foo.MyNotNull");
    myFixture.configureByText("a.java", """
      class Foo {
        static void foo(String[] ar<caret>ray) {
          System.out.println(array.length);
        }
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Insert '@MyNotNull'"));
    myFixture.checkResult("""
                            import foo.MyNotNull;
                                
                            class Foo {
                              static void foo(String @MyNotNull [] array) {
                                System.out.println(array.length);
                              }
                            }""");
  }

  public void test_type_use_qualified_type() {
    myFixture.addClass("package foo; import java.lang.annotation.*;@Target(ElementType.TYPE_USE)public @interface MyNotNull {}");
    NullableNotNullManager.getInstance(getProject()).setNotNulls("foo.MyNotNull");
    myFixture.configureByText("a.java", """
      import org.jetbrains.annotations.Contract;
      import foo.MyNotNull;

      class Sample {
          class Inner {
              String a;

              Inner(String a) {
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
      }""");
    assert myFixture.filterAvailableIntentions("Insert '@MyNotNull'").isEmpty();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      NullableNotNullManager.getInstance(getProject()).setNotNulls(ArrayUtil.EMPTY_STRING_ARRAY);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
