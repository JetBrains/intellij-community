import java.util.Objects;

public class SimpleObjectSafeComparison {
  boolean eq(Object a, Object b) {
    return Objects.equals(a, b);
  }
}