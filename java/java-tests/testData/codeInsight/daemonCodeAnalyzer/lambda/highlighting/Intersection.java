import java.io.Serializable;

class Test {

  interface I {
    void foo();
  }

  interface A {
    void bar(int i);
  }

  {
    Object o1 = (Serializable & I) () -> {};
    Object o2 = (I & Serializable) () -> {};
    Object o3 = (I & Runnable) <error descr="Multiple non-overriding abstract methods found in I & Runnable">() -> {}</error>;
    Object o4 = (A & Runnable) <error descr="Multiple non-overriding abstract methods found in A & Runnable">() -> {}</error>;
    Object o5 = (Runnable & A) <error descr="Multiple non-overriding abstract methods found in Runnable & A">() -> {}</error>;
  }
}

class Test1 {

  interface A {
    <X> void foo();
  }

  interface B {
    void foo();
  }

  {
    Object c0 = (A & B) ()->{};
  }
}
