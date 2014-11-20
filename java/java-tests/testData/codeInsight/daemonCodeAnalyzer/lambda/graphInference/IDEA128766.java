class Test {

  static class TKey<T> {}

  public interface Getter {
    <T> T getValue(TKey<T> key);
  }

  public static <TK extends TKey<?>> TK getAKey(TK tKeySuffix) {
    return tKeySuffix;
  }

  static final TKey<Double> KEY_D = new TKey<>();
  public static void f(Getter getter) {
    double d1 = getter.getValue(KEY_D);
    double d2 = getter.getValue(getAKey(KEY_D));
    TKey<Double> aKey = getAKey(KEY_D);
    double d3 = getter.getValue(aKey);
  }
}

