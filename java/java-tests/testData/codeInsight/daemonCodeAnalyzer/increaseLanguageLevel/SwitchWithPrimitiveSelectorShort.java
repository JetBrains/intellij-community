class X {
  void test(short i) {
    switch(i<caret>){
      default -> System.out.println("1");
    }
  }
}