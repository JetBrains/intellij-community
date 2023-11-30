class ExpectedParenthesizedExpr {

  public void test(Integer i) {
    double x = 1 + (<caret>double) (i * 2) / 5;
    System.out.println(x);
  }
}