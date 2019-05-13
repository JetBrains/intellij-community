class Test {
  void bar() {
    foo(1);
  }
  
  void foo(int i) {
    if (i > 0) {
      <selection>
       System.out.println(i);
      System.out.println(i);
      </selection>
    }
  }
}