// "Create 'default' branch" "true"
class Test {
  void test(I i) {
    switch (<caret>i){
      case Sub1 s1:
        break;
      case Sub2 s2:
        break;
    }
  }
}

sealed abstract class I {
}

final class Sub1 extends I {
}

final class Sub2 extends I {
}