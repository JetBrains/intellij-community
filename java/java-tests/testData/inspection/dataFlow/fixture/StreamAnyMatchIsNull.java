import java.math.BigInteger;
import java.util.Objects;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

class Test {
  @Nullable
  BigInteger calculate(@Nullable Boolean first, @Nullable BigInteger second, @Nullable BigInteger third) {
    if (Stream.of(first, second, third).anyMatch(Objects::isNull)) {
      return null;
    }
    return (first ? second : BigInteger.ZERO).add(third);
  }

  void simpler(@Nullable Boolean b) {
    Object x = b;
    if (x != null) {
      System.out.println(b.hashCode());
    }
  }
}
