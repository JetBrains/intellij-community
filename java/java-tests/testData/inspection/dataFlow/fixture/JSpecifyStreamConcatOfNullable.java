import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

@NullMarked
class JSpecifyStreamConcatOfNullable {
  long concatOfNullable(Integer first, @Nullable Integer second) {
    return Stream.concat(Stream.ofNullable(first), Stream.ofNullable(second))
      .filter(value -> value != 0)
      .count();
  }

  long concatOf(Integer first, @Nullable Integer second) {
    return Stream.concat(Stream.of(first), Stream.of(second))
      .filter(value -> <warning descr="Unboxing of 'value' may produce 'NullPointerException'">value</warning> != 0)
      .count();
  }
}
