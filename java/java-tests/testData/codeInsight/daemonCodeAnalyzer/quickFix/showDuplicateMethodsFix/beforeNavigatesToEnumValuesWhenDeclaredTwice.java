// "Show 'values()' duplicates|->Line #8" "true"

enum MyEnum {
  FIRST;
  public MyEnum[] values<caret>() {
    return null;
  }
  public MyEnum[] values() {
    return null;
  }
}