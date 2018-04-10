// "Convert to local" "true"
class Temp {

  void foo(int <caret>x) {
    x = 5;
    System.out.println(x);
  }

  void bar() {
    foo(2);
  }
}