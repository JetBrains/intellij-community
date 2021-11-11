// "Create missing switch branch 'Ab'" "true"
sealed interface A {}
sealed interface Aa extends A {}
final class Aaa implements Aa {}
final class Aab implements Aa {}
final class Ab implements A {}

public class Test {
  void test(A a) {
    switch (a<caret>) {
      case Aaa x -> {
      }
      case Aab x -> {
      }
    }
  }
}