// "Replace 'collect(summingDouble())' with 'mapToDouble().sum()'" "true"

import java.util.stream.*;

public class Main {
  void from(Stream<Double> stream) {
    stream.collect(Collectors.<caret>summingDouble(Double::doubleValue));
  }
}