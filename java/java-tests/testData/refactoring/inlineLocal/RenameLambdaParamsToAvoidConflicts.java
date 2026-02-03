class MyClass {
  void m() {
    Consumer c = o -> {
      System.out.println(o);
    };
    Object o = null;
    Consumer cc = <caret>c;
  }
}

interface Consumer {
  void accept(Object o);
}
