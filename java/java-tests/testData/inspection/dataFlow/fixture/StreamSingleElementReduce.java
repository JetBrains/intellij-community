import java.util.stream.*;
import org.jetbrains.annotations.*;

class Test {
  public static void main(String... args) {
    // Stream.of("elvis") gets Stream<@NotNull String> type, as "elvis" is not null. As reduce() doesn't introduce a new type parameter,
    // we have a warning here.
    String res = Stream.of("elvis").reduce(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>, (a, b) -> b);

    if (<warning descr="Condition 'res.equals(\"elvis\")' is always 'true'">res.equals("elvis")</warning>) {}

    String res2 = Stream.<@Nullable String>of("elvis").reduce(null, (a, b) -> b);
  }
}