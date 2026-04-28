class ForInitWithMultipleExpressions {
  void foo() {
    int i = 0;
    for (System.out.println("+"), System.out.println("+"); i < 3; i++) {
      System.out.println(i);
    }
  }
}
