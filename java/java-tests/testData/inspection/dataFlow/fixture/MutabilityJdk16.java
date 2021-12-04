package java.util.stream;

import java.util.List;

//mock
class Stream<T> implements BaseStream<T> {
  public static native <T> Stream<T> of(T t);
  native List<T> toList();
}
interface BaseStream<T>{}

public class MutabilityJdk16 {
  static final List<Integer> list = Stream.of(1).toList();

  void testFieldList(){
    list.<warning descr="Immutable object is modified">add</warning>(4);
  }

  void testToList() {
    List<Integer> l = Stream.of(1)
      .toList();
    l.<warning descr="Immutable object is modified">add</warning>(4);
  }
}
