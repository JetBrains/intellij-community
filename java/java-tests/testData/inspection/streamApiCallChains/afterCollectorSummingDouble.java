// "Replace 'collect(summingDouble())' with 'mapToDouble().sum()'" "true-preview"

import java.util.stream.*;

public class Main {
  void from(Stream<Double> stream) {
    stream.mapToDouble(Double::doubleValue).sum();
  }
}