
import java.util.Set;

public class WrongGenerics {

  @SuppressWarnings("unchecked")
  <T> Set<Foo<? extends T>> foo(Set<Foo<?>> foo) {
    return <error descr="Inconvertible types; cannot cast 'java.util.Set<Foo<?>>' to 'java.util.Set<Foo<? extends T>>'">(Set<Foo<?  extends T>>)foo</error>;
  }

  @SuppressWarnings("unchecked")
  <T> Set<Foo<? extends T>> bar(Set<Foo<? extends T>> foo) {
    return <error descr="Inconvertible types; cannot cast 'java.util.Set<Foo<? extends T>>' to 'java.util.Set<Foo<?>>'">(Set<Foo<?>>) foo</error>;
  }

  @SuppressWarnings("unchecked")
  <T> Foo<? extends T> bothSucceed(Foo<?> foo) {
    return (Foo<? extends  T>) foo;
  }

  @SuppressWarnings("unchecked")
  <T> Foo<Foo<? extends T>> bothFail(Foo<Foo<?>> foo) {
    return <error descr="Inconvertible types; cannot cast 'Foo<Foo<?>>' to 'Foo<Foo<? extends T>>'">(Foo<Foo<? extends T>>) foo</error>;
  }

  @SuppressWarnings("unchecked")
  <T> Set<Foo<? extends T>> onlyIntelliJSucceeds(Set<Foo<?>> foo) {
    return <error descr="Inconvertible types; cannot cast 'java.util.Set<Foo<?>>' to 'java.util.Set<Foo<? extends T>>'">(Set<Foo<? extends T>>) foo</error>;
  }
}

class Foo<T> {
}
