class ExposeProblem {

  public abstract class Parent<A>{}

  public abstract class Child<B, A> extends Parent<A> {
    public <C> void method(final Parent<? extends B> f) {}
    public <C> void method(final Child<C, ? extends B> f) {}
  }

  void call(){
    Child<String, Integer> f1 = null;
    Child<Integer, String> f2 = null;
    f1.method(f2);  // <= This is where the error is shown.
  }
}