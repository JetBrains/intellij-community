// "Create 'default' branch" "true"
class Test {
  void test(I i) {
    switch (i){
      case Sub1 s1:
        break;
      case Sub2 s2:
        break;
        default:
            throw new IllegalStateException("Unexpected value: " + i);
    }
  }
}

sealed abstract class I {
}

final class Sub1 extends I {
}

final class Sub2 extends I {
}