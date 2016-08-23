interface DumbAware {}
interface MyRunnable {
  String foo();
}
interface DumbAwareRunnable extends MyRunnable, DumbAware {}
interface DumbAwareRunnable2 extends DumbAwareRunnable {}

interface DumbAwareFunction extends DumbAware {
  Object f(Object o);
}

class Foo {
  <T extends DumbAware> void foo(T r, Class<T> c) {}
  void bar(DumbAwareRunnable r) {}
  void bar2(DumbAwareRunnable2 r) {}

  {
    DumbAwareRunnable var1 = () -> "var1";
    DumbAwareRunnable2 var2 = () -> "var2";
    DumbAwareFunction var3 = a -> "var3";
    WithDefaultMethods var4 = () -> "var4";

    foo(() -> "c1", DumbAwareRunnable2.class);
    bar(() -> "c2");
    bar2(() -> "c3");
  }
}

interface WithManyMethods {
  void run1();
  void run2();
  void run3();
}
interface WithManyMethods2 extends WithManyMethods {
}

interface WithDefaultMethods extends WithManyMethods2 {
  default String foo() {}
  default void run1();
  default void run2();
}