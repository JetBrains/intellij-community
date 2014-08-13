import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {
  private static class Thing {
    final String val;
    public Thing(String val) {
      this.val = val;
    }
  }

  public static Optional<List<String>> highlights() {
    return Optional.of(Collections.singletonList(new Thing("Hello")))
      .map(l -> l
        .stream()
        .map(t -> t.val + " world!")
        .collect(Collectors.toList()));
  }

  public static Optional<List<String>> works() {
    return Optional.of(Collections.singletonList(new Thing("Hello")))
      .map(l -> l
        .stream()
        .map(t -> t.val + " world!")
        .collect(Collectors.toList()));
  }
}