// "Replace with 'foreach'" "true"
import java.util.*;

public class Test extends ArrayList<String> {
  public void print() {
    new Runnable() {
      @Override
      public void run() {
          for (String s : Test.this) {
              System.out.println(s);
          }
      }
    };
  }
}