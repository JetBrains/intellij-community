// "Delete return value" "true"

class Test {

  void foo(boolean b) {
    if (b) return;
    System.out.println("bar");
  }
}