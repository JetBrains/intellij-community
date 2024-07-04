class X {
  void test(long i) {
    switch(i<caret>){
      default -> System.out.println("1");
    }
  }
}