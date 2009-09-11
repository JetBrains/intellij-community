import java.util.*;
class B<V> implements A<V> {
  public V getT(){return null;}
  void foo(List<B<V>> list) {
    for(B<V> b : list){
      V v = b.getT();
    }
  }
}