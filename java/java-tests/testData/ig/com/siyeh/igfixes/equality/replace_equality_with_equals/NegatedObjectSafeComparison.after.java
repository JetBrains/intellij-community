import java.util.Objects;

public class NegatedObjectSafeComparison {

  boolean a(Object a, Object b) {
    return !Objects.equals(a, b);
  }
}