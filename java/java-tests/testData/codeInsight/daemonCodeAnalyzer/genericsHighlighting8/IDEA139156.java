import java.util.List;

interface A<T extends List<String>> { }

class C {
  List<A<?>> foo(List<A<? extends Iterable<?>>> x) {
    return x;
  }
}