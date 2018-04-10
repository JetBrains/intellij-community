import javax.annotation.Nonnegative;
import java.util.Random;

public class LongRangeAnnotation {
  @Nonnegative int method() {
    return new Random().nextInt(100);
  }

  @Nonnegative int x;
  @Nonnegative double y;

  void testField() {
    if(<warning descr="Condition 'x < 0' is always 'false'">x < 0</warning>) {
      System.out.println("Impossible");
    }
    if(y < 0) {
      System.out.println("Doubles are not supported yet");
    }
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