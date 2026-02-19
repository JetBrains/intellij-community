class LabeledForLoop {

  public void test() {
      int i = 0;
      LABEL:
      while (i < 10) {
        System.out.println("Hello!");
          i++;
      }
  }
}