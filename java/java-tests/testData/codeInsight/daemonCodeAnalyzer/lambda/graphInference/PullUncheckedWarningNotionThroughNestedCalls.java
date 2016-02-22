import java.util.List;

class Temp<K> {

  public static List<String> foo() {
    return parallelizePairs(asList(new Tuple())).partitionBy();
  }

  public static <T> List<T> asList(T a) {
    return null;
  }

  public static <K> Temp<K> parallelizePairs(List<Tuple<K>> list) {
    return null;
  }

  public List<K> partitionBy() { return null; }
}

class Tuple<A> {
  public Tuple() {}
}
