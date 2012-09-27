class MyTest {
  interface I {
    String m(Foo<String> f);
  }

  class Foo<X> {
    String foo() {
      return null;
    }

    {
      I i = Foo<String> :: foo;
      <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest.I'">I i1 = Foo<Integer> :: foo;</error>
    }
  }
}
