// "Show 'foo()' duplicates" "false"

enum MyEnum {
  FIRST;
  public MyEnum valueOf<caret>(String s) {
    return null;
  }
}