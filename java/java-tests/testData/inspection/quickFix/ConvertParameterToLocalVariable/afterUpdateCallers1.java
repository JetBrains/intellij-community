// "Convert to local variable" "true"
class Temp {

  void foo(int k) {
      int x = 5;
    System.out.println(x);
  }

  void bar() {
    foo(42);
  }
}