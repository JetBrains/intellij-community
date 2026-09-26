class PolyadicExpression {
  void m(Object arg) {
    if (Math.random() > 0.5 /*1*/ && /*2*/ arg != null<caret> && /* :-) */ arg instanceof String && arg.equals(arg)) {
      System.out.println("warning");
    }
  }
}