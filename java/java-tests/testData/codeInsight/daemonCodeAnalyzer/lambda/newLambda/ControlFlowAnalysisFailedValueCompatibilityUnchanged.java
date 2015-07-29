import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public static void processDifference(final Stream<String> stream, final Stream<String> cells) {
    stream.map(rule -> {
      try {
        return cells.collect(Collectors.toMap(c -> c, null));
      } finally {
        System.out.println(<error descr="')' expected"><error descr="Expression expected">;</error></error><error descr="Unexpected token">)</error>;
      }
    });
  }

}