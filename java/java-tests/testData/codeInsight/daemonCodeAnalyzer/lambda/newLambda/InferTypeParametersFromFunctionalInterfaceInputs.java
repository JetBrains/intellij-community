import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

class A {
  {
    run(A.class, this, (o) -> {
      o.meth();
    <error descr="Missing return statement">}</error>);


    Map<Integer, Integer> map = Stream.<error descr="Incompatible types. Found: 'java.util.stream.Stream<java.lang.Integer>', required: 'java.util.Map<java.lang.Integer,java.lang.Integer>'">iterate</error>(5, t -> t + 5);
  }


  void meth() {}

  <T> T run(Class<T> c, T t, Function<T, T> f) {
    return f.apply(t);
  }
}
