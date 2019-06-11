// "Extract common part from 'if'" "INFORMATION"

class X {
  void foo() {
    if<caret> (true) {
      another(1);
      bar();
    }
    else {
      another(2);
      bar();
    }
  }

  void another(int i) {}

  void bar() {}
}