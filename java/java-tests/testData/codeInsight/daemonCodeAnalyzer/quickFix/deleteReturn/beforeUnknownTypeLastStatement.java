// "Delete return statement" "true-preview"

class Test {

  void foo(boolean b) {
    return<caret> null;
  }
}