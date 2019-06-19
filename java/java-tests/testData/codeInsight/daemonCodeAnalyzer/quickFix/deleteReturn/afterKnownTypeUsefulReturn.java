// "Delete return value '"foo"'" "true"

class Test {

  void foo(boolean b) {
    if (b) return;
    System.out.println("bar");
  }
}