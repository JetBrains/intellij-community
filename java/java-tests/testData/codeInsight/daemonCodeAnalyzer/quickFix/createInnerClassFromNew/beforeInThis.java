// "Create Inner Class 'Generic'" "true"
class Test {
  Test() {
    this (new <caret>Generic<String> ());
  }

  Test(String s){}
}