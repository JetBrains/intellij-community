
class Test {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  public void test() {
    MyAutoCloseable closeable = create();
    try<caret> {
      System.out.println(1);
    }
    finally {
      closeable.close();
    }
    closeable.close();
  }
}