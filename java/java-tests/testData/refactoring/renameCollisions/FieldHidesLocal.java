class A4 {
  protected final int <caret>a = 1;
}

class B {
  void main() {
    final int b = 2;
    new A4() {
      void m() {
        System.out.println(b);
      }
    };
  }
}
