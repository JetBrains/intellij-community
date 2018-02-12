// "Replace with count()" "true"
import java.util.Arrays;

public class Test {
  public void test() {
    long count = 0;
    for(int <caret>i=0; i<10; i++) {
      for(long l = 0; l < i; l++) {
        for(String s : Arrays.asList("x", "y", "z")) {
          for(int k = 0; k < s.length(); k++) {
            count++;
          }
        }
      }
    }
    System.out.println(count);
  }
}
