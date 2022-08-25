// "Create missing branches: 'B', and 'C'" "true"
abstract sealed class A {}
final class B extends A {}
final class C extends A {}

class Test {
  void test(A a) {
    switch (a) {
        case B b -> {
        }
        case C c -> {
        }
    }
  }
}