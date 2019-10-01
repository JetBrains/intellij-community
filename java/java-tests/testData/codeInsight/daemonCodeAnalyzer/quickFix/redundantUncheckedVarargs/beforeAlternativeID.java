// "Remove 'boxing' suppression" "true"
import java.util.ArrayList;

class Test {
  @SuppressWarnings( {"bo<caret>xing"})
  void doSomething() {
    final Integer number = Integer.valueOf(1);
    System.out.println(number);
  }
}