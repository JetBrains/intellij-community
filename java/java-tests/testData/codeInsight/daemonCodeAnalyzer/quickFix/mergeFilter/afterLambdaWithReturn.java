// "Merge filter chain" "true-preview"
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StreamFilter {
  List<Integer> calc(Stream<Integer> stream) {
    return stream
      .filter(chain -> chain.doubleValue() > 12 && shouldReport(chain))
      .collect(Collectors.toList());
  }

  private boolean shouldReport(Integer chain1) {
    return false;
  }
}