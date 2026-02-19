package com.siyeh.igtest.performance.method_may_be_static;


import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

public class MethodMayBeStatic implements Serializable {

    private void writeObject(java.io.ObjectOutputStream out)
            throws IOException {
        System.out.println("out");
    }

    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        System.out.println();
    }

    Object writeReplace() throws ObjectStreamException {
        return null;
    }

    Object readResolve() throws ObjectStreamException {
        return null;
    }
    native void f();
    void <warning descr="Method 'g()' may be 'static'">g</warning>() {
        System.out.println("boo!");
    }
}
class C {
  public int getInt() { return 5; }
}
class D extends C implements Surprise {
}
interface Surprise {
  int getInt();
}

interface FromJava8 {
  default void <warning descr="Method 'foo()' may be 'static'">foo</warning>() {
    System.out.println();
  }
}
class B {
  public void accept(String t) {
    System.out.println(t);
  }
}
class V extends B implements Consumer<String> {}
interface Consumer<T> {
  void accept(T t);
}
class Y {

  private void <warning descr="Method 'x()' may be 'static'">x</warning>() {
    new Object() {
      String s;
      void z() {
        s.hashCode();
      }
    };
  }
}
class X {
  private void test() {
    new X() {
      void run() {
        foo();
        test();
        X.this.test();
      }
    };
  }

  native void foo();
}
class Xx {
  private void <warning descr="Method 'test()' may be 'static'">test</warning>() {
    new Xx() {
      void run() {
        foo(); // super.foo(), not Xx.this.foo()
        test();
      }
    };
  }

  native void foo();
}


interface IntSupplier {
  Object[] supply(int dim);
}
class WithArrayReference {
  private void <warning descr="Method 'foo()' may be 'static'">foo</warning>() {
    IntSupplier aNew = Object[]::new;
  }
}
class InnerClassScope {
  class Inner {}
  
  Inner create() {
    return new Inner();
  }
}