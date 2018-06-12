// "Replace with a null check" "true"
import java.util.stream.Stream;

class Test {
  void test(String s) {
    if(String.class.isInsta<caret>nce(s)) {
      System.out.println("not null s");
    }
  }
}