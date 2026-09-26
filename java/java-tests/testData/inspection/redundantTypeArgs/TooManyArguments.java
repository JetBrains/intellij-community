import java.util.Map;
class ContainerUtil {
    public static <K, V> Map<K, V> newHashMap(Pair<? extends K, ? extends V> first, Pair<? extends K, ? extends V>... entries) {
        return null;
    }
}

record Pair<A, B>(A first, B second) {
    public static <A, B> Pair<A, B> pair(A first, B second) {
        return new Pair<>(first, second);
    }
}

class Test {
  Map<String, Integer> map = ContainerUtil.<caret><String, Integer>newHashMap(
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1),
              Pair.pair("str",1)
              );
}