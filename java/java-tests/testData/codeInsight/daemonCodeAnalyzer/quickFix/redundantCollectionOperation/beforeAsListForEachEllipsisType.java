// "Unwrap" "true"
import java.util.Arrays;

class Test {
  void test(String[] data, Object[] data2, boolean b) {
    List<Object> list = Arrays.as<caret>List(b ? data : data2);
    for(Object obj : list) {
      System.out.println(obj);
    }
  }
}