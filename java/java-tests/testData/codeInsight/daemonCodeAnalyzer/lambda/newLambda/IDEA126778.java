import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;

class Test {
  private static Map<Long, Long> multiDataPointsToPerTxnSummedValue(final Stream<Map.Entry<DateTime, Map<Long, Long>>> stream) {
    return stream.flatMap(e -> e.getValue().entrySet().stream().map(ex ->
                                                                      new Object() {
                                                                        Long txId = ex.getKey();
                                                                        DataPoint dataPoint = new DataPoint(e.getKey(), ex.getValue());
                                                                      }
    )).collect(groupingBy(t -> t.txId, mapping(t -> t.dataPoint, Collectors.summingLong(dataPoint -> dataPoint.getValue().longValue()))));
  }

  static class MultiDataPoint {}
  static class DateTime {}

  static class DataPoint {
    DataPoint(DateTime t, Long val) {}
    public Long getValue() {
      return null;
    }
  }
}