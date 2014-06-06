import pack.Foo;
class Test {
  {
    m(new Foo<String>() {
        @Override
        public void foo(String s) {
            
        }
    })
  }
  void m(Foo<String> foo){}
}