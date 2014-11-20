class Test {

  interface I<T> { void m(T arg); }

  <T1> void foo(T1 arg, java.io.Serializable x) {}
  <T2> void foo(I<T2> arg, Cloneable x) {}

  void test(int[] array) {
    this.<String>foo<error descr="Cannot resolve method 'foo(<lambda expression>, int[])'">(p -> {}, array)</error>;
  }
}