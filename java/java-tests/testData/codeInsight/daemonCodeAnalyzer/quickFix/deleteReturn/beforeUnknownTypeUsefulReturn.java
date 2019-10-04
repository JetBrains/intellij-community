// "Delete return value" "true"

class Test {

  void foo(boolean b) {
    if (b) return<caret> null;
    System.out.println("bar");
  }
}