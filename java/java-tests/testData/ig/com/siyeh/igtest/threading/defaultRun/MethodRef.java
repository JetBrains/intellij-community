package com.siyeh.igtest.threading.defaultRun;
class TestMethodRef {
  {
    new Thread(this::method2);
  }

  public void method2() {
    System.out.println("I am in thread");
  }
}