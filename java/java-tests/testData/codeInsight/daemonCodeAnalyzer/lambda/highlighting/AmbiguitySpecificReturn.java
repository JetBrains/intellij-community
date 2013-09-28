class IntStream {
  private void foo(IntStream s) {
    s.map<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">(i -> <error descr="Operator '<<' cannot be applied to 'int', '<lambda parameter>'">1 << i</error>)</error>;
    s.map<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">(i -> 1)</error>;
    s.map<error descr="Ambiguous method call: both 'IntStream.map(IntUnaryOperator)' and 'IntStream.map(ObjIntFunction<Integer>)' match">(i -> i)</error>;
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

