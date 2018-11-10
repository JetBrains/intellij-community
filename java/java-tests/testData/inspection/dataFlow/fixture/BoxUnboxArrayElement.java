class Test {
  private final static Integer[] DATA = {119, 7, 3};
  private final static int[] DATA2 = {119, 7, 3};

  void test() {
    for(int i=0; i<DATA.length; i++) {
      Integer val = DATA[i];
      int val2 = DATA2[i];
      if (val != val2) {
        System.out.println("Trouble");
      }
    }
  }
}