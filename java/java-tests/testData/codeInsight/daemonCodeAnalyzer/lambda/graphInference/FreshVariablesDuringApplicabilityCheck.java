
import java.util.List;

abstract class Foo {

  abstract  <V> List<V> createList();


  <T extends Comparable<T>> void sorted(List<T> list) { }

  {
    sorted(createList());
  }
}