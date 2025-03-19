// "Collapse into loop" "true-preview"
class A {
  String getPath() {
    Integer i = 0;
    <caret>i = 1;
    i = 2;
    i = 3;
    i = ;
    return i.toString()
  }
}