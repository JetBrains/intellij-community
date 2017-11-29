import java.lang.String;

interface I<A, B>  {
  B foo(A a);
}
class Foo<T> {
  public <V> Foo<V> map(I<T, V> mapper) {
    return new Foo<V>();
  }
}

class NoInferenceResult {

    <A, B> I<A, B> m(I<A, B>  f) { return null; }
    <T> void m1(T t) { }

    void test() {
        m(<error descr="Object is not a functional interface">(String s1) ->  (String s2) ->  s1 + s2</error>);
        m(<error descr="Object is not a functional interface">(String s1) ->  {return (String s2) ->  s1 + s2;}</error>);

        m((String s1) -> s1.length());
        m((String s1) -> s1);

        m1(<error descr="Object is not a functional interface">() -> { }</error>);

        Foo<String> foo = new Foo<String>();
        foo.map(v -> null);
        Foo<String> map1 = foo.map(value -> value + ", " + value);
    }
}
