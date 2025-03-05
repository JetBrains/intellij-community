class MyTest {
  MyTest(int a, int b, int c) {
  }

  {
    new MyTest(1, <error descr="Expected 3 arguments but found 2">""</error>);
  }
}