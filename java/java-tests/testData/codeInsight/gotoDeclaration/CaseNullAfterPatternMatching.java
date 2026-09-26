class Main {
  private static final int i = 0;
  private void f(Object o) {
    switch (o) {
      case Integer i :
        System.out.println();
      case null:
        System.out.println(i<caret>);
    };
  }
}