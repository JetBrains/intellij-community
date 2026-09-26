// "Show 'values()' duplicates|->Line #8" "true"

enum MyEnum {
  FIRST;
  public MyEnum[] values() {
    return null;
  }
  public MyEnum[] <selection><caret>values</selection>() {
    return null;
  }
}