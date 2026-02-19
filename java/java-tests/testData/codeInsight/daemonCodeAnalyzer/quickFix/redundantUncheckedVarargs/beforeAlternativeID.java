// "Remove 'boxing' suppression" "true-preview"
import java.util.ArrayList;

class Test {
  @SuppressWarnings( {"bo<caret>xing"})
  void doSomething() {
    final Integer number = Integer.valueOf(1);
    System.out.println(number);
  }
}