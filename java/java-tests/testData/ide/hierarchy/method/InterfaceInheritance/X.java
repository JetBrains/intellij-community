interface A {
  void foo();
}
interface B {
  void bar();
}
interface C extends A, B {}
interface D {
  void bar(String arg);
}
interface E extends D, C {
  default void bar() {}
}
abstract class F implements E {
  public void bar() {}
}