import java.math.BigInteger;
import java.util.function.Function;

class Test {
  long test(Object obj) {
    if (obj instanceof Integer || obj instanceof Long) {
      ((Number) obj).longValue()
    }
    return -1;
  }
}