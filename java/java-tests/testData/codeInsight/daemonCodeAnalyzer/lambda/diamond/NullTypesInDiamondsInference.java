
import java.util.List;
import java.util.Set;

class Test {
  void foo(List<Set<String>> dictSeqs) {
    final Dict<String> dict = new Dict<>(dictSeqs.toArray(new Set[dictSeqs.size()]));
    final Dict<String> dict1 = new Dict<String>(dictSeqs.toArray(new Set[dictSeqs.size()]));
  }

  static class Dict<K extends Comparable<K>> {
    public Dict(final Set<K>... sex) {}
  }
}
