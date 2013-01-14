class IntStream {
  private void foo(IntStream s) {
    s.map(i -> 1 << i);
    s.map(i -> 1);
    s.map(i -> i);
  }

  public static void main(String[] args) {
    new IntStream().foo(null);
  }

  private IntStream map(IntUnaryOperator mapper) {
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

