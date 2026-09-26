class MayChangeSemantics {

  void test(Random r, boolean b) {
    double value<caret> = r.nextDouble();
    if (b) {
      System.out.println(value);
    }
  }
}