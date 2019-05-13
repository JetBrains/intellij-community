import java.util.*;

class Test {
  interface Condition<K> {}
  class IOC<M> implements Condition {}

  static <T> List<T> filter(T[] c, Condition<? super T> con) {
    return null;
  }

  interface OE {}
  interface LOE extends OE {}

  void foo(OE[] es, IOC<LOE> con) {
    List<LOE> l = filter(es, con);
  }
}
