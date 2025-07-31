// "Navigate to duplicate method" "false"

enum MyEnum {
  FIRST;
  public MyEnum[] values<caret>() {
    return null;
  }
}