// "Add constructor parameter" "true"
class A {
  private final int field;
  private int j;

  A(int field) {
    this(0, field);
  }

  A(int j, int field) {
    this.j = j;
      this.field = field;
  }
}