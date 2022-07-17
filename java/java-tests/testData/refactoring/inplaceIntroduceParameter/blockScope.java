class Demo {
  void caller() {
    test(1);
  }
  
  void test(int x) {
    if (x > 0) {
      System.out.println("hello");
      System.out.println("hell<caret>o");
    } else {
      System.out.println("hello");
    }
  }
}