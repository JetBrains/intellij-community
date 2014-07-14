import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

class Test {
  private static final String[] decimalStrings = {"1.1", "+1.25"};

  {
    Arrays.stream(decimalStrings).map(BigDecimal::new).reduce(BigDecimal::add).ifPresent(bd -> System.out.print("Sum is " + bd));
  }

  void foo(final ArrayList<Pair> points) {
    points.stream().filter(p -> p.x > 0).collect(Collectors.toCollection(ArrayList::new));
  }
}

class Pair {
  int x;
}