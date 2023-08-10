class Main {
  private static final int i = 0;
  private void f(Object o) {
    switch (o) {
      case Integer i, null -> System.out.println(i<caret>);
    };
  }
}