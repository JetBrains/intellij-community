// "Add constructor parameter" "true"
class A {
  private final int field;
  private int j;

  A(int field) {
    this(field, 0);
  }

  A(int field, int j) {
      this.field = field;
      this.j = j;
  }
}