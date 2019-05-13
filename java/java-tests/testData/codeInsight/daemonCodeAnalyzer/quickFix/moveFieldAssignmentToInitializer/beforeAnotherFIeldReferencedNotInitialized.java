// "Move assignment to field declaration" "false"
public class Test {
  String myField;
  private String value;

  void f() {
    <caret>value = myField.trim();
  }
}
