class C<X, any Y> { }

class Test {
  void m(C<?, any> c) { }

  void test() {
    C<String, int> c = new C<String, int>();
    m(c);
  }
}