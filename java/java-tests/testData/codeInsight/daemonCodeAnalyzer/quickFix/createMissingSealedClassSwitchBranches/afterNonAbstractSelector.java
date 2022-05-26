// "Create missing branches: 'A', 'B', and 'C'" "true"
sealed class A {}
final class B extends A {}
final class C extends A {}

class Test {
  void test(A a) {
    switch (a) {
      case B b && b.hashCode() > 21 -> {}
        case B b -> {
        }
        case C c -> {
        }
        case A a1 -> {
        }
    }
  }
}