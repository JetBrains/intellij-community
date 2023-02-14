// "Delete return value" "true-preview"

class Test {

  void foo(boolean b) {
    if (b) return<caret> "foo";
    System.out.println("bar");
  }
}