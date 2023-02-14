class MyTest {
  MyTest(int a, int b, int c) {
  }

  {
    new MyTest(1, <error descr="'MyTest(int, int, int)' in 'MyTest' cannot be applied to '(int, java.lang.String)'">""</error>);
  }
}