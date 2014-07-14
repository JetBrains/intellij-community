interface I {
  void m(int x);
}

class Test {
  public static void main(String[] args) {
    I i = x -> <caret>
  }
}