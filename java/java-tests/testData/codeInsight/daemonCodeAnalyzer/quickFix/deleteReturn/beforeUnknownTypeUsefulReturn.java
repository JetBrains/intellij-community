// "Delete return value" "true-preview"

class Test {

  void foo(boolean b) {
    if (b) return<caret> null;
    System.out.println("bar");
  }
}