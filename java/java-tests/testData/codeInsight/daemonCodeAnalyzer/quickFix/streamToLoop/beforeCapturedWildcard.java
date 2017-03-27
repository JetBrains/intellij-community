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
    return set.asList()
      .<caret>stream()
      .mapToInt(Index::asInteger)
      .min();
  }
}