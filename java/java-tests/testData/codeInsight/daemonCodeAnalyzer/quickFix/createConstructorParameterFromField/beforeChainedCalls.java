// "Add constructor parameter" "true"
class A {
  private final int <caret>field;
  private int j;

  A() {
    this(0);
  }

  A(int j) {
    this.j = j;
  }
}