package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

class MyCloseable implements AutoCloseable {
  public MyCloseable(MyCloseable cl) {}

  @Override
  public void close() throws Exception {

  }
}

public class InnerTry {
  void doSome(String f) throws Exception {
    MyCloseable cl1 = null;
    try<caret> (MyCloseable cl2 = new MyCloseable(null)) {
      cl1 = new MyCloseable(cl2);
    } finally {
      if (cl1 != null) {
        cl1.close();
      }
    }
  }
}