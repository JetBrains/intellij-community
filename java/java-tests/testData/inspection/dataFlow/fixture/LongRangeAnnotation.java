import javax.annotation.Nonnegative;
import java.util.Random;

public class LongRangeAnnotation {
  @Nonnegative int method() {
    return new Random().nextInt(100);
  }

  void testAnnotated() {
    int value = method();
    if(value == 0) {
      System.out.println("Zero");
    } else if(<warning descr="Condition 'value > 0' is always 'true'">value > 0</warning>) {
      System.out.println("Positive");
    }
  }
}