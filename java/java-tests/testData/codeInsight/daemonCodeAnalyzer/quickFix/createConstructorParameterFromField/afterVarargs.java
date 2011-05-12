// "Add constructor parameter" "true"
class A {
  private final int field;
  A(int field, String... strs) {
      this.field = field;<caret>
  }

}