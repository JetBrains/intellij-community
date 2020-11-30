import java.util.*;

interface Foo<T> {
  static <E> Foo<E> of(E e1) {
    return null;
  }
  
  T get(int i);
  
}
class MainTest {
  {
    Optional.of("one,two").map(Foo::<String>of).get().get(0).split(",");
    Optional.of("one,two").map(Foo::of).get().get(0).split(",");
  }
}
