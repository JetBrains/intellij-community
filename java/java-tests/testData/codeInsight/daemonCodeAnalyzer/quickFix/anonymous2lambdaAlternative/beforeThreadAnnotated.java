// "Replace anonymous class with constructor accepting lambda" "false"
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Main {
  @Retention(RetentionPolicy.RUNTIME)
  @interface Anno{}

  public void testThread() {
    new <caret>Thread() {
      @Override
      @Anno
      public void run() {
        System.out.println("Hello from thread! "+x);
      }
    }.start();
  }
}