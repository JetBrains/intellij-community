// "Show 'valueOf()' duplicates|->Line #8" "true"

enum MyEnum {
  FIRST;
  public MyEnum valueOf(String s) {
    return null;
  }
  public MyEnum <selection><caret>valueOf</selection>(String s) {
    return null;
  }
}