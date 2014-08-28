import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test
{
  public static class K<T>
  {
    private final T head;
    private final K<T> tail;

    public K(T head, K<T> tail)
    {
      this.head = head;
      this.tail = tail;
    }
  }

  public static class P<U>
  {
    public <B, C> P<C> biMap(P<B> that, BiFunction<U, B, C> f)
    {
      return null;
    }
  }

  public static <A> P<K<A>> f(K<P<A>> x)
  {
    return x.head.biMap(f(x.tail), K::new);
  }
}

class A<T>
{
  public A(Supplier<T> arg0, Supplier<A<T>> arg1){}

  static <S> A<S> make(S[] s)
  {
    return helpMake(0, s);
  }

  static <S> A<S> helpMake(int offset, S[] s)
  {
    return new A<>(() -> s[offset], () -> helpMake(offset + 1, s));
  }
}

interface MultivaluedMap<K, V> extends Map<K, List<V>> {

  void putSingle(K var1, V var2);

  void add(K var1, V var2);

  V getFirst(K var1);
}


class Headers {
  private final Map<String, List<String>> headers;

  public Headers(Map<String, List<String>> headers) {
    this.headers = headers;
  }

  public Headers(MultivaluedMap<String, Object> multimap) {
    this.headers = multimap.entrySet()
      .stream()
      .collect(
        Collectors.toMap(
          Map.Entry::getKey,
          x -> x.getValue()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toList())
        )
      );
  }
}

class IDEA128245 {
  public void testCollectors(final Stream<Map.Entry<String, Set<String>>> stream,
                             Stream<Integer> integerStream) {
    stream.collect(Collectors.toMap(Map.Entry::getKey, entry -> integerStream.collect(Collectors.toSet())));
  }
}
