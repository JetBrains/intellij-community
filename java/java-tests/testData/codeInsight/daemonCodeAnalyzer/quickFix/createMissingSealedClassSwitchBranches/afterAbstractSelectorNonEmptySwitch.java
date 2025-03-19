// "Create missing branch 'Ab'" "true-preview"
sealed interface A {}
sealed interface Aa extends A {}
final class Aaa implements Aa {}
final class Aab implements Aa {}
final class Ab implements A {}

class Test {
  void test(A a) {
    switch (a) {
      case Aaa x -> {
      }
      case Aab x -> {
      }
        case Ab ab -> {
        }
    }
  }
}