public class Test {
  void foo(Object o) {
    if (o instanceof A) {
      <selection>((A)o).bar();</selection>
    }
  }
}

class A {
  void bar(){}
}