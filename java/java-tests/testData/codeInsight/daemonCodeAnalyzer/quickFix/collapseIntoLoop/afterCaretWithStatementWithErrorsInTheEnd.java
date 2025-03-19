// "Collapse into loop" "true-preview"
class A {
  String getPath() {
    Integer i = 0;
      for (int j = 1; j < 4; j++) {
          i = j;
      }
      i = ;
    return i.toString()
  }
}