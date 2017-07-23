// "Move assignment to field declaration" "INFORMATION"
public class Test {
  String myField;
  private String value;

  void f() {
    <caret>myField = getValue();
  }

  public static String getValue() {
    return value;
  }
}
