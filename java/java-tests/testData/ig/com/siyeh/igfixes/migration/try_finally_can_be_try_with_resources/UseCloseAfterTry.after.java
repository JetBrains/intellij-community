
class Test {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  public void test() {
    MyAutoCloseable closeable = create();
      try<caret> (closeable) {
          System.out.println(1);
      }
    closeable.close();
  }
}