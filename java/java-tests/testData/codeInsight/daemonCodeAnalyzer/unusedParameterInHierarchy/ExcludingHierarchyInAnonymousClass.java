class Test {
  public static void main(String[] args) {
    I i2 = new I() {
      @Override
      public void foo(int <warning descr="Parameter 'p' is never used"><caret>p</warning>) {
      }
    };
    System.out.println(i2);
  }
}

interface I {
  void foo(int i);
}