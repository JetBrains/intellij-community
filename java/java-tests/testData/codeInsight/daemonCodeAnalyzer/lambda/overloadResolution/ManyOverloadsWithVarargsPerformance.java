class Test {
  {
    ImmutableList.of(
      Pair.of(' ', ImmutableMap.of()),
      Pair.of(',', ImmutableMap.of()),
      Pair.of('<', ImmutableMap.of()),
      Pair.of('>', ImmutableMap.of()),
      Pair.of('/', ImmutableMap.of("streetVanity", "/")),
      Pair.of('?', ImmutableMap.of()),
      Pair.of(';', ImmutableMap.of("streetVanity", ",")),
      Pair.of(':', ImmutableMap.of()),
      Pair.of('\\', ImmutableMap.of()),
      Pair.of('|', ImmutableMap.of()),
      Pair.of('-', ImmutableMap.of()),
      Pair.of('_', ImmutableMap.of()),
      Pair.of('!', ImmutableMap.of()),
      Pair.of('@', ImmutableMap.of()),
      Pair.of('#', ImmutableMap.of())
    );
  }
}

class Pair<A, B> {
  public static <C, D> Pair<C, D> of(C c, D d) {return null;}
}

class ImmutableList<S> {
  public static <E> ImmutableList<E> of() {return null;}
  public static <E> ImmutableList<E> of(E e1) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10, E e11) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10, E e11, E e12) {return null;}
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10, E e11, E e12, E... o) {return null;}
}

class ImmutableMap<T, S> {
  public static <E, K> ImmutableMap<E, K> of() {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1) {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1, E e2, K k2) {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1, E e2, K k2, E e3, K k3) {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1, E e2, K k2, E e3, K k3, E e4, K k4) {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1, E e2, K k2, E e3, K k3, E e4, K k4, E e5, K k5) {return null;}
  public static <E, K> ImmutableMap<E, K> of(E e1, K k1, E e2, K k2, E e3, K k3, E e4, K k4, E e5, K k5, E e6, K k6) {return null;}

}