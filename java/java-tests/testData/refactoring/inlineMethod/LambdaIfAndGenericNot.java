import java.util.function.*;
import java.util.*;

class InlineTest {
  Predicate<List<String>> getPredicate(String pivot) {
    return list -> list != null && !has<caret>Greater(list, pivot);
  }

  <T extends Comparable<T>> boolean hasGreater(List<T> list, T pivot) {
    for (T t : list) {
      if (t.compareTo(pivot) > 0) return true;
    }
    return false;
  }
}