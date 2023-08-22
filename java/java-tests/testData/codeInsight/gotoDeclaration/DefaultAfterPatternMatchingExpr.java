class Main {
  private static final int i = 0;
  private void f(Object o) {
    switch (o) {
      case Integer i, default -> System.out.println(i<caret>);
    };
  }
}