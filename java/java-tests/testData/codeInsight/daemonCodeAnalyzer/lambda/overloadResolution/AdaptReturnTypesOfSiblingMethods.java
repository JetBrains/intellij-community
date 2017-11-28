
import java.util.Collections;
import java.util.List;

interface A<T, ID> {
  Iterable<T> findAll(Iterable<ID> ids);
}

interface B<T, ID> {
  List<T> findAll(Iterable<ID> ids);
}

interface C<T> extends B<T, Integer>, A<T, Integer> {}

interface D extends C<String> {}

class Test {
  public void foo(D d, C<String> c) {
    List<Integer> ids = Collections.emptyList();
    List<String> strings  = d.findAll(ids);
    List<String> strings1 = c.findAll(ids);
  }
}