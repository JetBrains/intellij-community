// "Replace lambda with method reference" "true"
import java.util.Objects;
import java.util.function.Predicate;

class Bar extends Random {
  public void test(Object obj) {
    Predicate<Object> pred = Objects::nonNull;
  }
}