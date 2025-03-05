class LambdaExpression {
  Runnable x() {
    return () -> {<caret>
      System.out.println();
    }
  }
}