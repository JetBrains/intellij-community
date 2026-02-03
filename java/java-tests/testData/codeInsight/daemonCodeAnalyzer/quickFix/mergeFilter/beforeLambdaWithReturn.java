// "Merge filter chain" "true-preview"
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StreamFilter {
  List<Integer> calc(Stream<Integer> stream) {
    return stream
      .<caret>filter(chain -> chain.doubleValue() > 12)
      .filter(chain1 -> {
        return shouldReport(chain1);
      })
      .collect(Collectors.toList());
  }

  private boolean shouldReport(Integer chain1) {
    return false;
  }
}