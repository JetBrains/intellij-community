package com.siyeh.igtest.memory.inner_class_may_be_static;

import javax.swing.*;

public class InnerClassMayBeStatic {
     static class Nested {
         public void foo() {
             bar("InnerClassMayBeStaticInspection.this");
         }

         private void bar(String string) {
         }
     }
}

class IDEADEV_5513 {

    private static class Inner  {

        private boolean b = false;

        private class InnerInner {

            public void foo() {
                b = true;
            }
        }
    }
}

class C extends JComponent {
    private class I {
        public void foo() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    repaint();
                }
            });
        }
    }
}
class D {

    void foo() {
        new Object() {
            class Y {}
        };
    }
}
class StaticInnerClass {

  private int foo;
  int bar;

  public class Baz extends StaticInnerClass  {
    Baz() {
      foo = -1;
    }
  }
  static class C extends StaticInnerClass {{
    bar = 1;
  }}
}
class SomeBeanUnitTest {

  private static class BeanCreator {

    public BeanCreator  withQuery() {
      return null;
    }
  }
}
class Outer {
  class A { // may be static
    B b;
  }
  class B extends  A {} // may not be static

  static class C { // may be static
    D b;
    class D extends C {}
  }

  static class E {
    G.F b;
    static class G { // may be static
      class F extends  E {}
    }
  }

  static class H { // may be static
    J.I b;
    class J {
      class I extends  H {}
    }
  }
}
class Complex {
  class C {
    void m() {
      Complex.super.toString();
    }
  }
  int i;
  static void n() {
  }

  private static class A {
    private A() {
    }
  }

  static class B {
  }

  static class F extends Complex {
    class G {
    }

    {
      A a = (A) null;
      G g = (G) null;
      new A() {};
      new B();

      i = 10;
      new E().m();
      Complex.n();
    }

    void m(A a) {
      a.toString();
    }

    class E {
      private void m() {
      }
    }
  }
}
class Test1<T> {
  class Inner {
    private final T test;
    public Inner(T test) {
      this.test = test;
    }
  }
}
class Test2 {
  static class Inner<T> {
    private final T test;
    public Inner(T test) {
      this.test = test;
    }
  }
}

class ImplicitConstructorReference {
  class A {
    C x = B::new;
  }

  interface C {
    B m();
  }

  static class B {}
}
class Scratch
{
  public static void main(String[] args)
  {
    class Inner
    {
      class Nested // can't be static
      {}
    }

  }
}
class JUnit5Test {
  @org.junit.jupiter.api.Nested
  class Inner {

  }
}
abstract class JavaClass<T> {
  public static class InnerClass<M> {}

  public static <K, L> JavaClass.InnerClass<L> baz(K t) {
    return null;
  }
}
class Simple {
  static class Inner {}

  void m() {
    new Inner();
  }

  static void s(Simple s) {
    new Inner();
  }
}
class X {
  X() {
    new Simple.Inner();
  }
}
class Usage {

  {
    new Node(0, new Node(1, null));
  }

  private static class Node {
    Node(int idx, Node next) {
    }
  }
}
class IdeaTest {

  public void test(){
    print(new InnerClass<Integer>().foo(Integer.valueOf(1)));
  }

  public void print(Integer foo){
    System.out.println(foo);
  }

  static class InnerClass<T>{
    public T foo(T bar){
      return bar;
    }
  }
}
class C1 {
  public C1(Feedback i) {
  }
}

class Feedback {
  String getOutputWindowName() {
    return null;
  }
}

class A {

  protected static class B extends C1 {

    public B() {
      super(new Feedback() {
              public void outputMessage() {
                getOutputWindowName();
              }
            }
      );
    }
  }
}