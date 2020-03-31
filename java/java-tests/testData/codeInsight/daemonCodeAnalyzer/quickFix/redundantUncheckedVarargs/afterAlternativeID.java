// "Remove 'boxing' suppression" "true"
import java.util.ArrayList;

class Test {
  void doSomething() {
    final Integer number = Integer.valueOf(1);
    System.out.println(number);
  }
}