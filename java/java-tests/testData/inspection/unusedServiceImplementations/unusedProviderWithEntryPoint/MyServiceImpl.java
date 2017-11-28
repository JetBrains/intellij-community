package my.impl;
import my.api.MyService;

public class MyServiceImpl {
  public static MyService provider() {
    return new MyService() {
      @Override
      public void foo() {
      }
    };
  }
}