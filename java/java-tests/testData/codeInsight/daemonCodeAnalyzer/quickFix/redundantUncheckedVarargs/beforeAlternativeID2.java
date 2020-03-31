// "Remove 'boxing' suppression" "false"
import java.util.ArrayList;

class Test {
  @SuppressWarnings( {"bo<caret>xing"})
  void doSomething() {
    final Integer number = 1;
    System.out.println(number);
  }
}