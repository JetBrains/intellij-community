class MyTest {
  final Runnable lambdaRunnable = () -> {
    System.out.println(<error descr="Variable 'o' might not have been initialized">o</error>);
  };

  final Object o;

  MyTest(Object o) {
    this.o = o;
  }
}