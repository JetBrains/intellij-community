// "Replace with 'foreach'" "true"
import java.util.*;

public class Test extends ArrayList<String> {
  public void print() {
    new Runnable() {
      @Override
      public void run() {
        fo<caret>r (int i = 0; i < size(); i++) {
          System.out.println(get(i));
        }
      }
    };
  }
}