import java.util.stream.IntStream;

// "Replace with forEach" "true"
class X {
  private void initBuilder() {
    StringBuilder builder = new StringBuilder();
    StringBuilder spaces = new StringBuilder();
      IntStream.range(0, 20).mapToObj(i -> "  ").forEach(spaces::append);
    builder.append(spaces);
  }
}