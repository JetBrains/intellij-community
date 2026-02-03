class Double {
  public static Double sum(Double a, Double b) {
    return null;
  }
}

interface BinaryOperator<T> extends BiFunction<T,T,T> {
}

interface BiFunction<T, U, R> {
    R apply(T t, U u);
}

class U {
  {
    BinaryOperator<Double> doubleBinaryOperator = Double::sum;
  }
}