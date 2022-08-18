// "Delete return value" "true-preview"

class Test {

  void foo(boolean b) {
    if (b) return;
    System.out.println("bar");
  }
}