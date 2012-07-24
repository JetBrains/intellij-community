class TypeArgsConsistency {

    interface I<T> {
        T m(int i, int j);
    }

    static void foo(I<Integer> s) { }

    static <X> I<X> bar(I<X> s) { return null; }

    {
      I<Integer> i1 = (i, j) -> i + j;
      foo((i, j) -> i + j);
      I<Integer> i2 = bar((i, j) -> i + j);
      I<Integer> i3 = bar<error descr="'bar(TypeArgsConsistency.I<java.lang.Integer>)' in 'TypeArgsConsistency' cannot be applied to '(<lambda expression>)'">((i, j) -> "" + i + j)</error>;
    }
}
