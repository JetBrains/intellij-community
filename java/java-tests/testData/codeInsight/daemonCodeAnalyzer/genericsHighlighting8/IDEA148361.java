
import java.util.function.Consumer;

class Foo {
  static <E extends Element> void add(Klass<?> klass, E e, Consumer<E> action) {}

  static <T> void bar(Klass<?> k, Method<T> m) {
    add(k, m, k::addMethod);
  }
}

interface Element {}
interface Method<M> extends Element {}
interface Type<T> {
  <M, C extends Type<T>> C addMethod(Method<M> m);
}
interface Klass<T> extends Type<T> {}

//simplified
class Foo1 {
  static void bar(Type1<?> k) {
    k.addMethod();
  }
}

interface Type1<T> {
  <C extends Type1<T>> C addMethod();
}