
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

class SameQualifiers {

  void m(List<? extends Foo> l) {
    map(l,  (<warning descr="Casting 'getFunction()' to 'Function<Foo, String>' is redundant">Function<Foo, String></warning>)getFunction());
  }
  
  static class Foo {}
  
  static  <T,V> void map(Collection<? extends T> collection, Function<? super T, ? extends V> mapping) {}
  private <T extends Foo> Function<T, String> getFunction() {
    return node -> node.toString();
  }
}
