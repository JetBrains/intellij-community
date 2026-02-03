import java.util.*;
class B<V> implements A<V> {
  public V getT(){return null;}
  void foo(List<A<V>> list) {
    for(A<V> b : list){
      V v = b.getT();
    }
  }
}