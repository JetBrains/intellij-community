// "Unwrap" "true"
import java.util.Arrays;

class Test {
  void test(String[] data) {
    List<String> list = Arrays.as<caret>List(data);
    for(String s : list) {
      System.out.println("hello "+s);
    }
    for(String s : list) {
      System.out.println("goodbye "+s);
    }
  }
}