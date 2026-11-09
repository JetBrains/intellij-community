package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnnecessaryThisInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryThisInspection());

  @Test
  public void testSimpleField() {
    inspectionFixture.get().doTest("class A {" +
           "  private int x;" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x = 3;" +
           "  }" +
           "}");
  }

  @Test
  public void testSimpleMethod() {
    inspectionFixture.get().doTest("class A {" +
           "  void x() {}" +
           "  void m() {" +
           "    /*'this' is unnecessary in this context*/this/**/.x();" +
           "  }" +
           "}");
  }

  @Test
  public void testParenthesesMethod() {
    inspectionFixture.get().doTest("class A {" +
           "  void x() {}" +
           "  void m() {" +
           "    (/*'this' is unnecessary in this context*/this/**/).x();" +
           "  }" +
           "}");
  }

  @Test
  public void testQualifiedThisNeeded() {
    inspectionFixture.get().doTest("class A {" +
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

  @Test
  public void testQualifiedThisNeeded2() {
    inspectionFixture.get().doTest("class A {" +
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

  @Test
  public void testQualifiedThisNotNeeded() {
    inspectionFixture.get().doTest("class A {" +
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

  @Test
  public void testQualifiedThisDifferentPackage() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("package foo;" +
                       "public abstract class Foo {" +
                       "        protected  void foo() {}" +
                       "}");
    inspectionFixture.get().doTest("package bar;" +
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
  @Test
  public void testCatchBlockParameter() {
    //noinspection EmptyTryBlock
    inspectionFixture.get().doTest("class A {" +
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

  @Test
  public void testForLoopParameter() {
    inspectionFixture.get().doTest("class A {" +
           "  private int i = 0;" +
           "  void m() {" +
           "    for (int i = 0; i < 10; i++) {" +
           "      this.i = 3;" +
           "    }" +
           "  }" +
           "}");
  }

  @Test
  public void testMethodParameter() {
    inspectionFixture.get().doTest("class A {" +
           "  private int i = 1;" +
           "  void m(int i) {" +
           "    this.i = 3;" +
           "  }" +
           "}");
  }

  @Test
  public void testLocalVariable() {
    inspectionFixture.get().doTest("class A {" +
           "  private int i = 2;" +
           "  void foo() {" +
           "    int i = 3;" +
           "    this.i=4;" +
           "  }" +
           "}");
  }


  @Test
  public void testLambdaMethodRefSelfRefs() {
    //noinspection FunctionalExpressionCanBeFolded
    inspectionFixture.get().doTest("class Main {" +
           "    Runnable lambdaExpression = () -> System.out.println(this.lambdaExpression);" +
           "    Runnable methodReference = this.methodReference::run;" +
           "}");
  }
  
  @Test
  public void testYield() {
    inspectionFixture.get().doTest("""
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

  @Test
  public void testNewExpression(){
    inspectionFixture.get().doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = /*'this' is unnecessary in this context*/this/**/.new Nested();" +
           "  }" +
           "}");
  }

  @Test
  public void testNewParenthesizedExpression(){
    inspectionFixture.get().doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = (/*'this' is unnecessary in this context*/this/**/).new Nested();" +
           "  }" +
           "}");
  }

  @Test
  public void testNewQualifiedExpression(){
    inspectionFixture.get().doTest("class Main {" +
           "  class Nested {}" +
           "  void test(){" +
           "    Nested nested = (/*'Main.this' is unnecessary in this context*/Main.this/**/).new Nested();" +
           "  }" +
           "}");
  }

  @Test
  public void testNewExpressionIsIgnored(){
    inspectionFixture.get().doTest("class Outer {" +
           "  class Nested {" +
           "    void test(){" +
           "      Outer.this.new Nested();" +
           "    }" +
           "  }" +
           "}");
  }
}