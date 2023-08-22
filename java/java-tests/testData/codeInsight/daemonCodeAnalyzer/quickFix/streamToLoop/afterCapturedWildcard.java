// "Replace Stream API chain with loop" "true-preview"

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
      for (Index index : set.asList()) {
          int integer = index.asInteger();
          if (!seen || integer < best) {
              seen = true;
              best = integer;
          }
      }
      return seen ? OptionalInt.of(best) : OptionalInt.empty();
  }
}