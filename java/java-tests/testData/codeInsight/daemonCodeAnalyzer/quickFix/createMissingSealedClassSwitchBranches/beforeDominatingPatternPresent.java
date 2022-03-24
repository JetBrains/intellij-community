// "Create missing switch branch 'Sub1'" "true"
sealed interface I {}
sealed interface J extends I {}
final class Sub1 implements I, J {}
final class Sub2 implements I {}

class Test {
  void test(I i) {
    switch (i<caret>) {
      case Sub2 sub2:
        break;
      case J j:
        break;
    }
  }
}