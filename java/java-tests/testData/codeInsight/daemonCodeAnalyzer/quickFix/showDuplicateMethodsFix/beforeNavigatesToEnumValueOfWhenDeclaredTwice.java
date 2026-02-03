// "Show 'valueOf()' duplicates|->Line #8" "true"

enum MyEnum {
  FIRST;
  public MyEnum valueOf<caret>(String s) {
    return null;
  }
  public MyEnum valueOf(String s) {
    return null;
  }
}