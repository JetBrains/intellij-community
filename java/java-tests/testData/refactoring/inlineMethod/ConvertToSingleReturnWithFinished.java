class A {
  String foo(int i) {
    if (i > 0) {
      if (i == 10) return null;
      System.out.println(i);
    }
    return String.valueOf(i);
  }

  void bar(int x) {
    if (x > 0) {
      System.out.println(f<caret>oo(x));
    }
    System.out.println("x < 0");
  }
}
