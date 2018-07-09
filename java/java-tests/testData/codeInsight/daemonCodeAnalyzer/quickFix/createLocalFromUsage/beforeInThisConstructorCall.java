// "Create local variable 'a'" "false"
class C {
  public C(int i) {
  }

  public C() {
    this(<caret>a);
  }
}
