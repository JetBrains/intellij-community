class Test {
  public static void main(String[] args) {
    I i1 = p -> System.out.println();
    System.out.println(i1);
    I i2 = new I() {
      @Override
      public void foo(int p) {
      }
    };
    System.out.println(i2);
  }

  static void foo(int <warning descr="Parameter 'i' is never used">i</warning>) {
  }

  public void bar(int <warning descr="Parameter 'i' is never used">i</warning>) {
  }
}

interface I {
  void foo(int i);
}

class A implements I {
  @Override
  public void foo(int i) {
  }
}