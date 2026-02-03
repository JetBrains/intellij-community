interface A {
  default void foo(){}
}

interface B extends A {
  <error descr="Static method 'foo()' in 'B' cannot override instance method 'foo()' in 'A'">static void foo()</error>{}
}