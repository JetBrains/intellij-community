package java.util.stream;

import java.util.List;
import java.util.Iterator;
import java.util.Spliterator;

//mock
class Stream<T> implements BaseStream<T, Stream<T>>{
  public native List<T> toList();

  public native Iterator<T> iterator();

  public native Spliterator<T> spliterator();

  public native boolean isParallel();

  public native Stream<T> sequential();

  public native Stream<T> parallel();

  public native Stream<T> unordered();

  public native Stream<T> onClose(Runnable var1);

  public native void close();
}

public class MutabilityJdk16 {

  private final List<Integer> list = new Stream<Integer>().toList();

  void testFieldList(){
    list.<warning descr="Immutable object is modified">add</warning>(4);
  }

  void testToList() {
    List<Integer> l = new Stream<Integer>()
      .toList();
    l.<warning descr="Immutable object is modified">add</warning>(4);
  }
}
