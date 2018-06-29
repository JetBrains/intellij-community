import java.io.IOException;

class MyTest<T> {
  public MyTest() throws IOException {}

  void m() {
    MyTest<String> test = new <error descr="Unhandled exception: java.io.IOException">MyTest<></error>();
  }

  {
    MyTest<String> test = new MyTest<>();
  }
}