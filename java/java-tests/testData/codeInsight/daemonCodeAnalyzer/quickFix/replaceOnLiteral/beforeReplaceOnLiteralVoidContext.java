// "Fix all 'Replacement operation has no effect' problems in file" "false"
class X {
  void test() {
    "c".<caret>replace("$", "/");
  }
}