class Test<A,B> {
    public static <P,Q> Test<P,Q> left(P p) { return null; }
    public static <P,Q> Test<P,Q> right(Q q) { return null; }
    public <C> C either(Function<A, C> leftFn, Function<B, C> rightFn){  return null; }
    public Test<B,A> swap() { 
        return either(Test::<B,A>right, Test::<B,A>left); 
    }
}

interface Function<T, R> {
  R fun(T t);
}
