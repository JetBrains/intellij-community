// "Navigate to duplicate method" "true"

enum MyEnum {
  FIRST;
  public MyEnum[] values<caret>() {
    return null;
  }
  public MyEnum[] values() {
    return null;
  }
}