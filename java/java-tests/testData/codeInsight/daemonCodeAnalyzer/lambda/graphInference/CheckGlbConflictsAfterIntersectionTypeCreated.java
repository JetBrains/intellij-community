
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class Test {
  private Map<FilterHelper, Integer> extractFilterCounts() {
    final Function<FilterData, Integer> count = f -> null;
    final Function<? super FilterData, ? extends FilterHelper> mapper = f -> null;
    final List<FilterData> rows = Collections.emptyList();
    return rows.stream().collect(Collectors.toMap(mapper, count));
  }

  private class FilterHelper {}

  private class FilterData {}
}

class Test1 {
  private void extractFilterCounts(final I<Integer> i1,
                                   final I<? super Integer> i2) {
    m(i2, i1);
  }

  private static <T> void m(I<? super T> i1, I<? super T> i2) { }

  static class I<T> {}
}
