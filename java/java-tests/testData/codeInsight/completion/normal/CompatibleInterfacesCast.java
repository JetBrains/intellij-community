interface Foo { void foo(); }
interface Bar { void bar(); }

public class A {
  void foo(Foo l) {
    if (l instanceof Bar) {
      l.<caret>
    }
  }
}
