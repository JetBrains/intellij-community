// "Unwrap" "true"
import java.util.Arrays;

class Test {
  void test(String[] data) {
    for(String s : Arrays.as<caret>List(data)) {
      System.out.println("hello "+s);
    }
  }
}