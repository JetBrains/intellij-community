class X {
  void test(boolean i) {
    switch(i<caret>){
      default -> System.out.println("1");
    }
  }
}