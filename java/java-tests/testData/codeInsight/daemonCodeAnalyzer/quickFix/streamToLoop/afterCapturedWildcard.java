// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.OptionalInt;

public class Main {
  interface Index {
    int asInteger();
  }
  interface IndexSet<S extends Index> {
    List<S> asList();
  }

  public static OptionalInt min(IndexSet<?> set) {
      boolean seen = false;
      int best = 0;
      for (Index index: set.asList()) {
          int asInteger = index.asInteger();
          if (!seen || asInteger < best) {
              seen = true;
              best = asInteger;
          }
      }
      return seen ? OptionalInt.of(best) : OptionalInt.empty();
  }
}