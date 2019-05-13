// "Replace lambda with method reference" "false"
import java.util.function.Predicate;

class Bar extends Random {
  public void test(Object obj) {
    Predicate<Object> pred = s -> obj != <caret>null;
  }
}