// "Delete return statement" "false"

class Test {

  void foo(boolean b) {
    return<caret> getWithSideEffects();
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}