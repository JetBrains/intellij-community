class ExpectedParenthesizedExpr {

  public void test(Integer i) {
    double x = 1 + i<caret> * 2 / 5;
    System.out.println(x);
  }
}