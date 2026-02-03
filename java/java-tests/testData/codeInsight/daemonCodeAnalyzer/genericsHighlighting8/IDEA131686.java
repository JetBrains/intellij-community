interface Msg<T extends Msg<T>> {}

class Conv<T extends Msg<T>> {
  static <A extends Msg<A>> Conv<A> createBar(A a) {
    return null;
  }

  @SuppressWarnings("unchecked")
  static void test() {
    Conv<? extends Msg<?>> conv = Conv.createBar((Msg) null);
  }
}
