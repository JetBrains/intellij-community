// "Assert 'field != null'" "false"
enum A {
  I(field.<caret>hashCode());
  private static Object field;
  A(int i) {}
}