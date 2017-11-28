// "Move assignment to field declaration" "true"
public class Test {
  String myField = "foo";
  private String value = myField.trim();

  void f() {
  }
}
