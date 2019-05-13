class Tmp {
  Integer toInt(Number num) {
    return null;
  }

  Stream<Number> test() {
    Stream<Number> numberStream = null;
    Stream<Number> integerStream1 = numberStream.map(this::toInt);
    Stream<Number> integerStream2 = numberStream.map(num -> toInt(num));

    return numberStream.map(this::toInt);
  }
}

interface Stream<T> {
  <R> Stream<R> map(Function<? super T, ? extends R> mapper);
}

interface Function<I, R> {
  R fun(I t);
}
