// "Replace with lambda" "false"
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class Test {

  @Retention(RetentionPolicy.RUNTIME)
  @interface A {}

  {
    Runnable r = new Ru<caret>nnable() {
      @A
      public void run() {
      }
    };
  }
}