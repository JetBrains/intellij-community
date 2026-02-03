// "Replace with expression lambda" "false"
interface A {
  int m(int x);
}

interface B {
  void m(boolean x);
}

abstract class X {
  abstract void foo(A j);
  abstract void foo(B i);

  void bar(Object o) {
    foo(x -> {
      retu<caret>rn x += 1;
    });
  }
}