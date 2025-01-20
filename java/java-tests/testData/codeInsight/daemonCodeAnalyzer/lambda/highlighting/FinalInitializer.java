class MyTest {
  final Runnable lambdaRunnable = () -> {
    System.out.println(<error descr="Cannot read value of field 'o' before the field's definition">o</error>);
  };

  final Object o;

  MyTest(Object o) {
    this.o = o;
  }
}