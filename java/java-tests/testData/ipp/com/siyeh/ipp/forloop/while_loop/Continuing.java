class Continuing {
  void testFor() {
    <caret>for (int i=0; i<10; i++) {
      if(i == 5) continue;
      System.out.println(i);
    }
  }
}