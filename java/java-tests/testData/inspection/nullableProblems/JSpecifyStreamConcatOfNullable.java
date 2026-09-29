import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

@NullMarked
class JSpecifyStreamConcatOfNullable {
  List<Integer> concatOfNullable(Integer first, @Nullable Integer second) {
    final List<Integer> ids = Stream.concat(Stream.ofNullable(first), Stream.ofNullable(second))
      .filter(value -> value != 0)
      .toList();
    return ids;
  }
}
