class Demo {
  void caller() {
    test(1, "hello");
  }
  
  void test(int x, String hello) {
    if (x > 0) {
      System.out.println(hello);
      System.out.println(hello);
    } else {
      System.out.println("hello");
    }
  }
}