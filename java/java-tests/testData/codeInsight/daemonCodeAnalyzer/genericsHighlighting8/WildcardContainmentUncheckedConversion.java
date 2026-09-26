// The declared bound of a type parameter decides the containment for an element conversion too, so none of the
// conversions below is unchecked. javac reports neither an error nor an unchecked warning for them.
import java.util.List;

interface Bounded<X extends CharSequence> {
  X get();
}

interface StrangeDouble<R extends CharSequence, S extends R> {
  R first();

  S second();
}

class WildcardContainmentUncheckedConversion {
  Bounded<? extends CharSequence> fromUnbounded(List<Bounded<?>> src) {
    for (Bounded<? extends CharSequence> each : src) {
      return each;
    }
    return src.get(0);
  }

  Bounded<? extends CharSequence> fromSuperBounded(List<Bounded<? super String>> src) {
    for (Bounded<? extends CharSequence> each : src) {
      return each;
    }
    return src.get(0);
  }

  StrangeDouble<?, ? extends CharSequence> fromTypeVariableBound(List<StrangeDouble<?, ?>> src) {
    for (StrangeDouble<?, ? extends CharSequence> each : src) {
      return each;
    }
    return src.get(0);
  }
}
