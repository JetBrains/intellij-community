class Main {
  void foo(List<? extends Number> list) {
    list.forEach(new Consumer<Number>() {
        @Override
        public void consume(Number number) {
            <caret>
        }
    });
  }
}

class List<T> {
  void forEach(Consumer<? super T> consumer);
}

interface Consumer<T> { void consume(T t); }