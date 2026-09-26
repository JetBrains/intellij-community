class MayChangeSemantics {

  void test(Random r, boolean b) {
    <caret>  if (b) {
          double value = r.nextDouble();
          System.out.println(value);
    }
  }
}