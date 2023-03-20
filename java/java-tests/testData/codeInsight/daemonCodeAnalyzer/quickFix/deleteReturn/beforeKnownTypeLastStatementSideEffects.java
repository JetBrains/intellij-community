// "Delete return statement and extract side effects" "true-preview"

class Test {

  void foo(boolean b) {
    return/*1*/<caret> getWithSideEffects(/*2*/);
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}