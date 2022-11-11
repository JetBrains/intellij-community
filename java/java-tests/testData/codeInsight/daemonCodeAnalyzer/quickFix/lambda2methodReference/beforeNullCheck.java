// "Replace lambda with method reference" "true-preview"
import java.util.function.Predicate;

class Bar extends Random {
  public void test(Object obj) {
    Predicate<Object> pred = s -> s != <caret>null;
  }
}