// "Create missing branches: 'Ab', and 'A'" "true-preview"
sealed class A {}
abstract sealed class Aa extends A {}
final class Aaa extends Aa {}
final class Aab extends Aa {}
final class Ab extends A {}

class Test {
  void test(A a) {
    switch (a) {
      case Aaa x -> {
      }
      case Aab x -> {
      }
        case Ab ab -> {
        }
        case A a1 -> {
        }
    }
  }
}