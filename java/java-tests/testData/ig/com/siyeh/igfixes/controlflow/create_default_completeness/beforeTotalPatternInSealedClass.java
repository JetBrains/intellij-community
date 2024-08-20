// "Create 'default' branch" "false"
class Test {
  void test(I i) {
    switch (<caret>i){
      case ((I i && true)):
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