package my.ext;
import my.api.MyService;

public class MyServiceExt {
  public static MyService provider() {
    return new MyService() {
      @Override
      public void foo() {
      }
    };
  }
}