interface I<T> {
  void m(T t);
}

class Test {
  public static void main(String[] args) {
    I<String> i = <caret>
  }
}