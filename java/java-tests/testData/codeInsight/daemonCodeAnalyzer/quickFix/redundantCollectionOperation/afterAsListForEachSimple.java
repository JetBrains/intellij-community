// "Unwrap" "true"
import java.util.Arrays;

class Test {
  void test(String[] data) {
    String[] list = data;
    for(String s : list) {
      System.out.println("hello "+s);
    }
    for(String s : list) {
      System.out.println("goodbye "+s);
    }
  }
}