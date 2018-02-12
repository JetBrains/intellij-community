class P<T, Self extends P<T, Self>> { }

class MM<T, H extends P<T, H>> {
  H last;

  void m(final Function<P<T, ?>, P<T, ?>> function) {
    generate(last, function);
  }
  public static <E> void generate(E first, Function<? super E, ? extends E> generator) { }

}
interface Function<Param, Result> {}