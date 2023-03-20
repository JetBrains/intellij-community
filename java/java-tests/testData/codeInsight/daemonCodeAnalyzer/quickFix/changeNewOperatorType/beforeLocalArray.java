// "Change 'new C[10]' to 'new C[]'" "false"

class X {

  C[] x() {
    class C {}
    return <caret>new C[10];
  }
}
