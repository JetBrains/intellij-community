class LambdaExpression {
  public void foo() {
    new ArrayList<String>().forEach((x) -> System.out.<caret>println(x));
  }
}