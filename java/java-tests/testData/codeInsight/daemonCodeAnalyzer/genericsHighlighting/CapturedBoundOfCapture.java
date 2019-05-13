
interface A<T extends String, K extends T> {
  K get();
}

class Test {
  void f(A<?, ?> a) {
    String s = a.get();
  }
}