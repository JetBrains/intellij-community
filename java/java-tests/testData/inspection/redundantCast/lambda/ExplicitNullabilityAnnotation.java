import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@NotNullByDefault
class Test {
  private static void processList(List<Integer> list) {
    Integer result = list.stream()
      // Nullability of "x" itself is NOT_NULL (because of @NotNullByDefault)
      // Casting it as simply "Integer" is redundant.
      // But casting it as "@Nullable Integer" changes nullability of return type of the "map" method call.
      .map(x -> (@Nullable Integer)x) // We want to prevent the 'redundant cast' warning on this line
      .reduce(null, (a, b) -> a == null ? b : Math.max(a, b));
    if (result != null) {
      System.out.println(result);
    }
    else {
      System.out.println("empty");
    }
  }

  public static void main(String[] args) {
    processList(Arrays.asList(1, 2, 3, 4, 5, 1, 2));
  }
}

@NotNullByDefault
abstract class C {

    @Nullable
    abstract C next();

    public Stream<C> forward() {
        return Stream.iterate(this, Objects::nonNull, C::next).map(c -> (@NotNull C) c); // We want to prevent the 'redundant cast' warning on this line
    }
}