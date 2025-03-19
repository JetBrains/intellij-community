class LabeledForLoop {

  public void test() {
    LABEL:
    <caret>for (int i = 0; i < 10; i++) {
      System.out.println("Hello!");
    }
  }
}