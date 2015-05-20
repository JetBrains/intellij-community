class Test {
  void bar() {
    foo(1);
  }

  void foo(int i) {
    <selection>
    if (i > 0) {
      System.out.println(i);
      System.out.println(i);
      return;
    }
    </selection>
    System.out.println("Hi");
  }
}