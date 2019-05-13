// "Move assignment to field declaration" "true"
public class Test {
  String myField = "foo";
  private String value;

  void f() {
    <caret>value = myField.trim();
  }
}
