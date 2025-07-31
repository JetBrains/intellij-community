// "Navigate to duplicate method" "true"

enum MyEnum {
  FIRST;
  public MyEnum valueOf<caret>(String s) {
    return null;
  }
  public MyEnum valueOf(String s) {
    return null;
  }
}