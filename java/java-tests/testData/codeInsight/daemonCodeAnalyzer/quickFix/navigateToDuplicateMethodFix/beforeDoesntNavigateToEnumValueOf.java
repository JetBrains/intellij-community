// "Navigate to duplicate method" "false"

enum MyEnum {
  FIRST;
  public MyEnum valueOf<caret>(String s) {
    return null;
  }
}