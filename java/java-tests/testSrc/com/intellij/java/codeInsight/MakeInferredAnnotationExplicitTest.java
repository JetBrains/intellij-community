package com.intellij.java.codeInsight;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MakeInferredAnnotationExplicitTest extends LightJavaCodeInsightFixtureTestCase {
  public void testContractAndNotNull() {
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

  public void testParameter() {
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

  public void testCustomNotNull() {
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

  public void testTypeUse() {
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

  public void testTypeUseQualifiedType() {
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

  public void testNoUnmodifiableInferenceWhenTypeAnnotated() {
    myFixture.configureByText("a.java", """
       import java.util.List;
       import org.jetbrains.annotations.NotNull;
       import org.jetbrains.annotations.Unmodifiable;

       public final class Example<A>
       {
           public <T> @Unmodifiable @NotNull List<T> <caret>genericInstanceMethod(T value)
           {
               return List.of(value);
           }
       }""");
    List<IntentionAction> actions =
      myFixture.filterAvailableIntentions("Insert '@Contract(value = \"_ -> new\", pure = true) @Unmodifiable'");
    assertEmpty(actions);
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
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
