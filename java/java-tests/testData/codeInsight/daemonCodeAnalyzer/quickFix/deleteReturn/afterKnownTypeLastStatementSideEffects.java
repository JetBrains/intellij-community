// "Delete return statement and extract side effects" "true-preview"

class Test {

  void foo(boolean b) {
      getWithSideEffects(/*2*/);/*1*/
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}