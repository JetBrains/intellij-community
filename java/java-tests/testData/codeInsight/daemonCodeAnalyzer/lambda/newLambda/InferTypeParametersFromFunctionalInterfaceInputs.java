import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

class A {
  {
    run(A.class, this, (o) -> {
      o.meth();
    <error descr="Missing return statement">}</error>);


    Map<Integer, Integer> map = Stream.iterate(5, <error descr="Incompatible types. Required Map<Integer, Integer> but 'iterate' was inferred to Stream<T>:
no instance(s) of type variable(s) T exist so that Stream<T> conforms to Map<Integer, Integer>">t -> t + 5</error>);
  }


  void meth() {}

  <T> T run(Class<T> c, T t, Function<T, T> f) {
    return f.apply(t);
  }
}
