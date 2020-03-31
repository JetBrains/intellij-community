// "Delete return value" "true"

class Test {

  void foo(boolean b) {
    if (b) return<caret> "foo";
    System.out.println("bar");
  }
}