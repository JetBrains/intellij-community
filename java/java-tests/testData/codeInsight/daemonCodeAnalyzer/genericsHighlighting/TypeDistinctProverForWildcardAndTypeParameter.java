abstract class Test {
  interface Selection<A> {}
  interface CriteriaQuery<T> {
    void select(Selection<? extends T> selection);
  }

  private <N> void foo(CriteriaQuery<N> criteria, Selection<String[]> array) {
    criteria.select((Selection<? extends N>)array);
  }

  private <N extends Integer> void foo1(CriteriaQuery<N> criteria, Selection<String[]> array) {
    criteria.select(<error descr="Inconvertible types; cannot cast 'Test.Selection<java.lang.String[]>' to 'Test.Selection<? extends N>'">(Selection<? extends N>)array</error>);
  }

  private <N extends Integer> void foo2(CriteriaQuery<N> criteria, Selection<Object> array) {
    criteria.select(<error descr="Inconvertible types; cannot cast 'Test.Selection<java.lang.Object>' to 'Test.Selection<? extends N>'">(Selection<? extends N>)array</error>);
  }
}