// "Create missing switch branch 'Sub1'" "true-preview"
sealed interface I {}
final class Sub1 implements I {}
final class Sub2 implements I {}

class Test {
  void test(I i) {
    switch (i) {
      case Sub1 sub1 && Math.random() > 0.5 -> {}
      case Sub1 sub1 && Math.random() > 0.1 -> {}
      case Sub1 sub1 && Math.random() > 0.5 -> {}
        case Sub1 sub1 -> {
        }
        case Sub2 sub2 -> {}
    }
  }
}