
import java.util.Comparator;

class MyTest<T> {
  {
    create<error descr="'create(java.util.Comparator<T>)' in 'MyTest' cannot be applied to '(java.util.Comparator<T>)'">(Comparator.naturalOrder())</error>;
  }

  static <T> void create(Comparator<T> c) {}
}
