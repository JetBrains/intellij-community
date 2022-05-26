// "Create missing branches: 'A', 'B', and 'C'" "true"
sealed class A {}
final class B extends A {}
final class C extends A {}

class Test {
  void test(A a) {
    switch (a<caret>) {
      case B b && b.hashCode() > 21 -> {}
    }
  }
}