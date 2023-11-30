package com.siyeh.ig.style;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnnecessaryThisInspectionTest extends LightJavaInspectionTestCase {

  public void testSimpleField() {
    doTest("class A {" +
           "  private int x;" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x = 3;" +
           "  }" +
           "}");
  }

  public void testSimpleMethod() {
    doTest("class A {" +
           "  void x() {}" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x();" +
           "  }" +
           "}");
  }

  public void testParenthesesMethod() {
    doTest("class A {" +
           "  void x() {}" +
           "  void m() {" +
           "    (/*'this' is unnecessary in this context*/this/**/).x();" +
           "  }" +
           "}");
  }

  public void testQualifiedThisNeeded() {
    doTest("class A {" +
           "    public void foo(String s) {}" +
           "    class D{" +
           "        public void foo(String s) {}" +
           "    }" +
           "    class C extends D {" +
           "        class Box {" +
           "            void bar() {" +
           "                A.this.foo(\"\");" +
           "            }" +
           "        }" +
           "    }" +
           "}");
  }

  public void testQualifiedThisNeeded2() {
    doTest("class A {" +
           "  private int x;" +
           "  public void x() {}" +
           "  class X {" +
           "    private int x;" +
           "    public void x(){}" +
           "    void y() {" +
           "      A.this.x = 4;" +
           "      A.this.x();" +
           "    }" +
           "  }" +
           "}");
  }

  public void testQualifiedThisNotNeeded() {
    doTest("class A {" +
           "    public void foo(String s) {}" +
           "    class D{" +
           "        private void foo(String s) {}" +
           "    }" +
           "    class C extends D {" +
           "        class Box {" +
           "            void bar() {" +
           "                /*'A.this' is unnecessary in this context*/A.this/**/.foo(\"\");" +
           "            }" +
           "        }" +
           "    }" +
           "}");
  }

  public void testQualifiedThisDifferentPackage() {
    myFixture.addClass("package foo;" +
                       "public abstract class Foo {" +
                       "        protected  void foo() {}" +
                       "}");
    doTest("package bar;" +
           "import foo.Foo;" +
           "final class Bar extends Foo {" +
           "    public void foo() {}" +
           "        final class InnerBar extends Foo {" +
           "                void bar() {" +
           "                        Bar.this.foo(); " +
           "                }" +
           "        }" +
           "}");
  }

  /**
   * IDEA-42154
   */
  public void testCatchBlockParameter() {
    //noinspection EmptyTryBlock
    doTest("class A {" +
           "    private Throwable throwable = null;" +
           "    public void method() {" +
           "        try {" +
           "        } catch (Throwable throwable) {" +
           "            this.throwable = throwable;" +
           "            throwable.printStackTrace();" +
           "        }" +
           "    }" +
           "}");
  }

  public void testForLoopParameter() {
    doTest("class A {" +
           "  private int i = 0;" +
           "  void m() {" +
           "    for (int i = 0; i < 10; i++) {" +
           "      this.i = 3;" +
           "    }" +
           "  }" +
           "}");
  }

  public void testMethodParameter() {
    doTest("class A {" +
           "  private int i = 1;" +
           "  void m(int i) {" +
           "    this.i = 3;" +
           "  }" +
           "}");
  }

  public void testLocalVariable() {
    doTest("class A {" +
           "  private int i = 2;" +
           "  void foo() {" +
           "    int i = 3;" +
           "    this.i=4;" +
           "  }" +
           "}");
  }


  public void testLambdaMethodRefSelfRefs() {
    //noinspection FunctionalExpressionCanBeFolded
    doTest("class Main {" +
           "    Runnable lambdaExpression = () -> System.out.println(this.lambdaExpression);" +
           "    Runnable methodReference = this.methodReference::run;" +
           "}");
  }
  
  public void testYield() {
    doTest("""
             class Main {
               void test() {
                 this.yield();
                 /*'this' is unnecessary in this context*/this/**/.yield1();
               }
              \s
               void yield() {}
               void yield1() {}
             }""");
  }

  public void testNewExpression(){
    doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = /*'this' is unnecessary in this context*/this/**/.new Nested();" +
           "  }" +
           "}");
  }

  public void testNewParenthesizedExpression(){
    doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = (/*'this' is unnecessary in this context*/this/**/).new Nested();" +
           "  }" +
           "}");
  }

  public void testNewQualifiedExpression(){
    doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = (/*'Main.this' is unnecessary in this context*/Main.this/**/).new Nested();" +
           "  }" +
           "}");
  }

  public void testNewExpressionIsIgnored(){
    doTest("class Outer {" +
           "  class Nested {" +
           "    void test(){" +
           "      Outer.this.new Nested();" +
           "    }" +
           "  }" +
           "}");
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new UnnecessaryThisInspection();
  }
}