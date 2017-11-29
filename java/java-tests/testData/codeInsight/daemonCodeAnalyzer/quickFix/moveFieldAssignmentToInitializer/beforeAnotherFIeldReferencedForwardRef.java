// "Move assignment to field declaration" "false"
public class Test {
  private String value;
  String myField = "foo";

  void f() {
    <caret>value = myField.trim();
  }
}
