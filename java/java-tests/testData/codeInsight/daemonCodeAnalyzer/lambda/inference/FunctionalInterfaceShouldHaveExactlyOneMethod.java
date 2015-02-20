public class NotAFIT {
  static class First {
    static interface A<T> {
      void foo1();
      void foo2();
    }
    static <T> void foo(A<T> a) {
    }

    void bar() {
        foo(<error descr="Multiple non-overriding abstract methods found in interface NotAFIT.First.A">() ->{}</error>);
    }
  }

  static class WithInheritance {
    static interface A<T> {
      void foo1();
    }

    static interface B<M> extends A<M> {
      void foo2();
    }

    static <T> void foo(B<T> a) {
    }

    void bar() {
      foo(<error descr="Multiple non-overriding abstract methods found in interface NotAFIT.WithInheritance.B">()->{}</error>);
    }
  }

  static class WithInheritanceOverrideSameMethod {
    static interface A<T> {
      void foo1();
    }

    static interface B<M> extends A<M> {
      void foo1();
    }

    static <T> void foo(B<T> a) {
    }

    void bar() {
      foo(()->{});
    }
  }
}