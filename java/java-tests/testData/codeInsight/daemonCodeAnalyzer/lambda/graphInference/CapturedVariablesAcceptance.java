
import java.util.List;

class Foo<K> {
  static <E> Foo<E> from(Iterable<? extends E> iterable) {
    return null;
  }

  void m(Iterable<? extends String> arguments, List<String> list){
    from(arguments).append<error descr="'append(java.lang.Iterable<? extends capture<? extends java.lang.String>>)' in 'Foo' cannot be applied to '(java.util.List<java.lang.String>)'">( list)</error>;
  }

  Foo<K> append(Iterable<? extends K> other) {
    return null;
  }

}