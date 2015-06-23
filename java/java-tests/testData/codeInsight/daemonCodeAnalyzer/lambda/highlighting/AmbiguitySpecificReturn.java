class IntStream {
  private void foo(IntStream s) {
    s.<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">map</error>(i -> 1 << i);
    s.<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">map</error>(i -> 1);
    s.<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">map</error>(i -> i);
  }

  public static void main(String[] args) {
    new IntStream().foo(null);
  }

  private IntStream <warning descr="Private method 'map(IntUnaryOperator)' is never used">map</warning>(IntUnaryOperator mapper) {
    System.out.println(mapper);
    return null;
  }

  private <T> IntStream <warning descr="Private method 'map(ObjIntFunction<T>)' is never used">map</warning>(ObjIntFunction<T> mapper) {
    System.out.println(mapper);
    return null;
  }
}

interface IntUnaryOperator {
  public int applyAsInt(int operand);
}

interface ObjIntFunction<T> {
  public T apply(int i);
}

