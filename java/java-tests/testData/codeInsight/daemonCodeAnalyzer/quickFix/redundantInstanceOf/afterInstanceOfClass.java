// "Replace with a null check" "true"
import java.util.stream.Stream;

class Test {
  void test(String s) {
    if(s != null) {
      System.out.println("not null s");
    }
  }
}