import java.util.function.*;

public class InstanceOfPattern {
  void test(Object obj) {
    if (obj instanceof Number n) {
      if (<warning descr="Condition 'n == obj' is always 'true'">n == obj</warning>) {}
    }
  }
}