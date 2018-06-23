class A {
  void foo() {
    if(Math.random() > 2) {
      System.out.println("xyz");
      return;
    }
    System.out.println("oops");
  }

  void bar(int x) {
    if (x > 0) {
      f<caret>oo();
    } else {
      System.out.println("x < 0");
    }
  }

  void baz(int x) {
    if (x > 0)
      if (x < 10) {
        foo();
      }
  }
}
