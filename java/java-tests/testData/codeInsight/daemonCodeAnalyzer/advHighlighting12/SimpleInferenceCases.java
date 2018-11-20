import java.util.function.*;
class MyTest {
  <T> T foo(T t) {
    return t;
  }
  <T> T foo(Supplier<T> t) {
        return t.get();
    }

  <T> T foo(IntSupplier t) {
    return null;
  }

  static <K> K bar() {
    return null;
  }

  void m(int i) {
    String s = foo(switch (i) {default -> "str";});
    String s1 = <error descr="Incompatible types. Required String but 'foo' was inferred to T:
no instance(s) of type variable(s)  exist so that Object conforms to String">foo(switch (i) {case 1 -> new Object(); default -> "str";});</error>
    String s2 =  foo(() -> switch (i) {
            default -> "str";
        });
    String s3 = foo(() -> switch (i) {default -> bar();});
  }
}