// "Replace with 'foreach'" "true"
import java.util.*;

public class Test extends ArrayList<String> {
  public void print() {
    fo<caret>r (int i = 0; i < size(); i++) {
      System.out.println(get(i));
    }
  }
}