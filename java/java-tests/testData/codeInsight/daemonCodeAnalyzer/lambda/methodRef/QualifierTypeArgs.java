import java.util.*;

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
      I i1 = Foo<Integer> :: <error descr="Cannot resolve method 'foo'">foo</error>;
    }
  }
}

class MyTest1 {
    interface I {
       String m(Foo f);
    }

    static class Foo<X> {
       String foo() { return null; }

       static void test() {
          I i = Foo::foo;
       }
    }
}

class MyTest2 {
  public static void main(String[] args) {
    Arrays.sort(new String[0], String.CASE_INSENSITIVE_ORDER::compare);
  }
}