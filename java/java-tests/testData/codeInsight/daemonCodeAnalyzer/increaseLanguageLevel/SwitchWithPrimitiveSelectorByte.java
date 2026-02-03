class X {
  void test(byte i) {
    switch(i<caret>){
      default -> System.out.println("1");
    }
  }
}