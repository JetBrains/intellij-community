class Outer <U, V> {


  Outer(Foo<? super U> a) {}
  Outer(Bar<? super U, ? extends V> a) {}

  {
    Outer<String, String> o = new Outer<>(new Foo<String>() {
        @Override
        public void m() {
            <caret>
        }
    });
  }
}

interface Foo<A> {
  void m();
}
interface Bar<A, B> {}
