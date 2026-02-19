// "Sort content" "true-preview"

enum B {
  ELEM_1<caret>(A.ELEM_1),
  ELEM_3(A.ELEM_3),
  ELEM_2(A.ELEM_2);

  private final A a;

  B(A a) {
    this.a = a;
  }

  public A getA() {
    return a;
  }
}