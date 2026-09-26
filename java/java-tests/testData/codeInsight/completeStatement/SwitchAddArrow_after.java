class X {
  sealed interface I0 {}
  sealed interface I1 extends I0 {}
  sealed interface I2 extends I1 {}
  record A() implements I2 {}
  record B() implements I0 {}
  static void main(I0 i1) {
    switch (i1) {
      case A a -> System.out.println("A");
        case B b -> {
            <caret>
        }
    }
  }
}
