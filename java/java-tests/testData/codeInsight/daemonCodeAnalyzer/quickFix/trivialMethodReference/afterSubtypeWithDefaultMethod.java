// "Fix all 'Functional expression can be folded' problems in file" "true"
import java.util.function.Predicate;

class Test {
  interface LoggingPredicate extends Predicate<String> {
    @Override
    default Predicate<String> negate() {
      System.out.println("negating!");
      return Predicate.super.negate();
    }
  }

  interface SubPredicate extends Predicate<String> {
  }

  public static void main(String[] args) {
    LoggingPredicate predicate = s -> !s.isEmpty();
    test(predicate::test);
    Predicate<String> copy = predicate;
    test(copy::test);
    SubPredicate subPredicate = s -> !s.isEmpty();
    test(subPredicate);
  }

  private static void test(Predicate<String> test) {
    System.out.println(test.negate().test("hello"));
  }
}