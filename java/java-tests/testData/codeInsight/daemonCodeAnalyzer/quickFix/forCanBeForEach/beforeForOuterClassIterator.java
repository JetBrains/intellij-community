// "Replace with 'foreach'" "true"
import java.util.*;

public class Test extends ArrayList<String> {
  public void print() {
    new Runnable() {
      @Override
      public void run() {
        fo<caret>r (Iterator<String> it = iterator(); it.hasNext(); ) {
          System.out.println(it.next());
        }
      }
    };
  }
}