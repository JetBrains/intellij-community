// "Create inner class 'Generic'" "true-preview"
class Test {
  Test() {
    this (new <caret>Generic<String> ());
  }

  Test(String s){}
}