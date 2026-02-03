class A {
  void foo() {
    if(Math.random() > 2) {
      System.out.println("xyz");
      return;
    }
    System.out.println("oops");
  }

  void bar(int x) {
    Runnable r = () -> {
      System.out.println("hi");
      f<caret>oo();
    };
  }
}
