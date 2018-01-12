// "Unwrap" "true"
import java.util.Arrays;

class Test {
  void test(String[] data, Object[] data2, boolean b) {
    Object[] list = b ? data : data2;
    for(Object obj : list) {
      System.out.println(obj);
    }
  }
}