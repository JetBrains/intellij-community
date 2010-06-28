// "Try to generify 'before1.java'" "true"

class c<T> {
  void put(T t ) {
  }
}

class Use {
  void f() {
    c c = new c();
    <caret>c.put("");
  }
}