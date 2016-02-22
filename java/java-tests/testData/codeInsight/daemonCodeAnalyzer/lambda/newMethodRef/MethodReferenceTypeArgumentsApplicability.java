import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

abstract class Test {
  void p(final Stream<List<Integer>> stream){
    stream.flatMap(Collection::<String>stream);
    stream.flatMap(Collection::<<error descr="Unexpected wildcard">? extends String</error>>stream);
    stream.flatMap(Collection::<<error descr="Unexpected wildcard">?</error>>stream);
    stream.flatMap(Collection::<<error descr="Unexpected wildcard">? super String</error>>stream);
  }

  static <T> void foo(T t) {}
  interface I {
    void m(String s);
  }

  {
    I i =  Test::<String>foo;
    I i1 = Test::<Integer><error descr="Invalid method reference: String cannot be converted to Integer">foo</error>;
    I i2 = Test::foo;

  }
}
