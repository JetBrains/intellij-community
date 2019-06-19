// "Delete return statement" "false"

class Test {

  void foo(boolean b) {
      if (b) return<caret> getWithSideEffects();
      System.out.println("bar");
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}