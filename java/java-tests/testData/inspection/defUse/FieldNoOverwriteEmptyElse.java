public class FieldNoOverwriteEmptyElse {
  boolean myField;

  void test(int x) {
    if (x != 1) {
      myField = true;
    }
    if (x == 3) {
      myField = false;
    } else {}
  }
}