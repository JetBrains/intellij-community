class Main {
  private static final int i = 0;
  private void f(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
      default:
        System.out.println(i<caret>);
    };
  }
}