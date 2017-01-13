// "Replace with 'foreach'" "true"
import java.util.*;

public class Test extends ArrayList<String> {
  public void print() {
      for (String s : this) {
          System.out.println(s);
      }
  }
}