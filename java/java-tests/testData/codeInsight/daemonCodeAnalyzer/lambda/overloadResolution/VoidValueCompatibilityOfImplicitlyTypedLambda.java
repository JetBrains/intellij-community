interface A {
  int m(int x);
}

interface B {
  void m(boolean x);
}

abstract class Test {
  abstract void foo(A j);
  abstract void foo(B i);

  void bar(Object o) {
    foo(x -> {
      return x += 1;
    });
    <error descr="Ambiguous method call: both 'Test.foo(A)' and 'Test.foo(B)' match">foo</error>(x -> x += 1);
    foo(<warning descr="Parameter 'x' is never used">x</warning> -> 1);
    foo(x -> <error descr="Operator '!' cannot be applied to 'int'">!x</error>);
    <error descr="Ambiguous method call: both 'Test.foo(A)' and 'Test.foo(B)' match">foo</error>(x -> ++x);
    foo(<warning descr="Parameter 'x' is never used">x</warning> -> o instanceof String ? 1 : 0);
  }
}
