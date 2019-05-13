// "Move assignment to field declaration" "INFORMATION"
public class Test {
  String myField = getValue();
  private String value;

  void f() {
  }

  public static String getValue() {
    return value;
  }
}
