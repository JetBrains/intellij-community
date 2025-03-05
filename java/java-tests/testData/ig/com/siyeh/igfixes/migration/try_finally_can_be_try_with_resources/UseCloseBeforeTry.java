
class Test {
  interface MyAutoCloseable extends AutoCloseable {
    @Override
    void close();
  }

  native MyAutoCloseable create();

  public void test() {
    MyAutoCloseable closeable = create();
    closeable.close();
    try<caret> {
      System.out.println(1);
    }
    finally {
      closeable.close();
    }
  }
}