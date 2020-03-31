// "Delete return value and extract side effects" "true"

class Test {

  void foo(boolean b) {
      if (b) /*1*/return ("foo"/*2*/) + <caret>getWithSideEffects(/*3*/);
      System.out.println("bar");
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}