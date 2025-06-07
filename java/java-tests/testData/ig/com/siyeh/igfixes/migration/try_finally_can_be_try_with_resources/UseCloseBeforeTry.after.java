
class Test {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  public void test() {
    MyAutoCloseable closeable = create();
      try (closeable) {
          closeable.close();
          System.out.println(1);
      }
  }
}