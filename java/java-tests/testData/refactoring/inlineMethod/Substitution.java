class Foo {
  public static <T, Loc> WeighingComparable<T, Loc> we<caret>igh(final Key<? extends Weigher<T, Loc>> key,
                                                          final Computable<T> element,
                                                          final Loc location) {
    return new WeighingComparable<T, Loc>(element, location, new Weigher[0]);
  }


  public WeighingComparable<String, ProximityLocation> method(boolean b,
                                                              final Computable<String> elementComputable,
                                                              Object processingContext) {
    return weigh(WEIGHER_KEY, elementComputable, new ProximityLocation());
  }

  public static final Key<ProximityWeigher> WEIGHER_KEY = null;
}

abstract class ProximityWeigher extends Weigher<String, ProximityLocation> {

}

class ProximityLocation {
}

class Key<P> {
}

class Weigher<A, B> {
}

class Computable<O> {}

class WeighingComparable<T, Loc> implements Comparable<WeighingComparable<T, Loc>> {

  public WeighingComparable(final Computable<T> element, final Loc location, final Weigher[] weighers) {
    
  }

  public int compareTo(@NotNull final WeighingComparable<T, Loc> comparable) {
    return 0;
  }

  @Nullable
  private Comparable getWeight(final int index) {
    return null;
  }
}
