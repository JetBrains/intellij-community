// "Extract common part from 'if'" "INFORMATION"

class X {
  void foo() {
    if (true) {
      another(1);
    }
    else {
      another(2);
    }
      bar();
  }

  void another(int i) {}

  void bar() {}
}