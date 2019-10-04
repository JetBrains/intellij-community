// "Delete return value and extract side effects" "true"

class Test {

  void foo(boolean b) {
      if (b) /*1*/ {
          getWithSideEffects(/*3*/);
          return /*2*/;
      }
      System.out.println("bar");
  }

  private String getWithSideEffects() {
    System.out.println("baz");
  }
}