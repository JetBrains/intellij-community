class Test {

  {
    Marker<String> cm = forEach(child1(), child2());
  }

  public static <D> Child1<D> child1() {
    return null;
  }

  public static <S> Child2<S> child2() {
    return null;
  }

  public static <F, CM extends Marker<F>> CM forEach(CM contents, CM cm) {
    return null;
  }

  interface Marker<A> {}
  static class Parent<S> {}

  static class Child1<D> extends Parent<ChildAttr1> implements Marker<D> {}
  interface ChildAttr1 {}

  static class Child2<S> extends Parent<ChildAttr2> implements Marker<S> {}
  interface ChildAttr2 {}

}
