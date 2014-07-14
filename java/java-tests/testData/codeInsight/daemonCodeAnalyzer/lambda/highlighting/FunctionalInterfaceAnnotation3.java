interface I<T, K extends Integer> {
    void m(T t);
    void m(K k);
  }

@FunctionalInterface
interface IEx extends I<Integer, Integer> { }

