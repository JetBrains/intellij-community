// "Move assignment to field declaration" "INFORMATION"
public class Test {
  String myField = getValue();
  private String value;

  void f() {
  }

  public String getValue() {
    return value+value;
  }
}
