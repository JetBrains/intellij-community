import pack.Foo;
class Test {
  {
    Foo<Objec> f = new Foo<Objec>() {
        @Override
        public void foo(Objec objec) {
            <caret>
        }
    }
  }
}