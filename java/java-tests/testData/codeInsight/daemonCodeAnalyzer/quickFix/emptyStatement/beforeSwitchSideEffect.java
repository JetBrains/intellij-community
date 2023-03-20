// "Extract side effect" "true-preview"
class Test {
  void test(int x) {
    s<caret>witch(/*1*/x/*2*/=/*3*/2/*4*/)/*5*/ {}
  }
}