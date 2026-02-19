// "Show 'values()' duplicates" "false"

enum MyEnum {
  FIRST;
  public MyEnum[] values<caret>() {
    return null;
  }
}