import java.util.stream.IntStream;

// "Collapse loop with stream 'forEach()'" "true-preview"
class X {
  private void initBuilder() {
    StringBuilder builder = new StringBuilder();
    StringBuilder spaces = new StringBuilder();
      IntStream.range(0, 20).mapToObj(i -> "  ").forEach(spaces::append);
    builder.append(spaces);
  }
}