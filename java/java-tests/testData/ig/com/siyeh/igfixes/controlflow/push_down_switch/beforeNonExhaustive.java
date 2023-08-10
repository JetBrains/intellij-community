// "Push down 'switch' expression" "false"
class X {
  enum E {A, B, C}

  void test(E e) {
    <caret>switch (e) {
      case A -> System.out.println(1);
      case B -> System.out.println(2);
    }
  }
}