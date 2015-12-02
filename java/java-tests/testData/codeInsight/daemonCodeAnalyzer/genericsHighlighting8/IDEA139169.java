
import java.util.List;

interface A<T extends A<T> & Cloneable> { }

class C {
  List<A<? extends A<?>>> foo(List<A<?>> x) {
    return x;
  }
}