class Main {
  void foo(List<? extends Number> list) {
    list.forEach(new <caret>);
  }
}

class List<T> {
  void forEach(Consumer<? super T> consumer);
}

interface Consumer<T> { void consume(T t); }