import java.io.IOException;

class MyTest<T> {
  public MyTest() throws IOException {}

  void m() {
    MyTest<String> test = <error descr="Unhandled exception: java.io.IOException">new MyTest<>()</error>;
  }

  {
    MyTest<String> test = new MyTest<>();
  }
}