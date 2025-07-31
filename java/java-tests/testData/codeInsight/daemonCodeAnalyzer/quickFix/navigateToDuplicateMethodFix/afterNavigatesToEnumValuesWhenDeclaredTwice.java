// "Navigate to duplicate method" "true"

enum MyEnum {
  FIRST;
  public MyEnum[] values() {
    return null;
  }
  public MyEnum[] <selection><caret>values</selection>() {
    return null;
  }
}