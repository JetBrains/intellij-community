import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

abstract class StreamsNull {
  @NotNullByDefault
  static class Streams {
    @SafeVarargs
    static native <T extends @Nullable Object> Stream<T> concat(Stream<? extends T>... streams);
  }

  List<String> calc(Stream<Leg> positions, Stream<Leg> leg1, Stream<Leg> leg2, double v) {
    return Streams.concat(
        positions.map(Leg::getInstrument),
        leg1.map(Leg::getInstrument),
        leg2.map(Leg::getInstrument)
      )
      .filter(i -> isInteresting(i, v))
      .toList();
  }

  List<String> calc2(Stream<Leg> positions, Stream<Leg> leg1, Stream<Leg> leg2, double v) {
    return Streams.concat(
        positions.map(Leg::getInstrument),
        leg1.map(Leg::getInstrument2),
        leg2.map(Leg::getInstrument)
      )
      .filter(i -> isInteresting(<warning descr="Argument 'i' might be null">i</warning>, v))
      .toList();
  }

  protected abstract boolean isInteresting(@NotNull String instrument,
                                           double v);
}

interface Leg {
  @NotNull
  String getInstrument();
  @Nullable
  String getInstrument2();
}