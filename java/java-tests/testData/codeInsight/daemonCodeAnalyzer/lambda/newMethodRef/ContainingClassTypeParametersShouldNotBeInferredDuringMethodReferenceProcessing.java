
import java.util.Arrays;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Stream;

class C {
  private static void foo(final Stream<Predicate<Boolean>> stream) {
    BinaryOperator<Predicate<Boolean>> bo = Predicate::and;
    Predicate<Boolean> notWorking = stream.reduce(Predicate::and).orElse(t -> true);
  }

  public static void bar(Stream<E> stream) {
    Object[] array = stream
      .sorted(Enum::compareTo)
      .toArray();
    System.out.println("array = " + Arrays.toString(array));
  }
  enum E {;}
}
