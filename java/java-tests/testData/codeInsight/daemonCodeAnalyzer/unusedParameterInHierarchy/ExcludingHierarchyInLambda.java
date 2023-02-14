class Test {
  public static void main(String[] args) {
    I i1 = <warning descr="Parameter 'p' is never used"><caret>p</warning> -> System.out.println();
    System.out.println(i1);
  }
}

interface I {
  void foo(int i);
}