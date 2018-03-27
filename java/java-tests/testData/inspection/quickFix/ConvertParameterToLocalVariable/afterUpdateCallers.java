// "Convert to local" "true"
class Temp {

  void foo() {
      <caret>int x = 5;
    System.out.println(x);
  }

  void bar() {
    foo();
  }
}