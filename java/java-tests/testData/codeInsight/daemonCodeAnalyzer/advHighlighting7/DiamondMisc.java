import java.util.ArrayList;
import java.util.List;

class Test {
  List<String> queue = new ArrayList<>();
  ArrayList l = new ArrayList<>(8);
}

class HMCopy<K, V> {
  private Entry[] table;

  class Entry<K, V> {
    Entry(int h, K k, V v, Entry<K, V> n) {
    }
  }

  void addEntry(int hash, K key, V value, int bucketIndex) {
    Entry<K, V> e = table[bucketIndex];
    table[bucketIndex] = new Entry<>(hash, key, value, e);
  }
}

class DD {
    P1<P<String>> l = new L<String>() {
        @Override
        void f() {
        }
    };

    P1<P<String>> l1 = new L<>();

    P1<P<String>> foo() {
        return new L<>();
    }

    String s = "";
}

class L<K> extends P1<P<K>> {
    void f() {
    }
}

class P1<P1T> extends P<P1T> {
}

class P<PT> {
}


class Test1 {
  void bar() {
    foo<error descr="'foo(F<F<java.lang.String>>)' in 'Test1' cannot be applied to '(FF<java.lang.Object>)'">(new FF<>())</error>;
  }

  void foo(F<F<String>> p) {}
}

class FF<X> extends F<X>{}
class F<T> {}

class MyTest {
     static class Foo<X> {
        Foo(X x) {}
     }

     static interface Base<Y> {}
     static class A extends Exception implements Base<String> {}
     static class B extends Exception implements Base<Integer> {}

     void m() throws B {
         try {
             if (true) {
                 throw new A();
             }
             else {
                 throw new B();
             }
         } catch (A ex) {
             Foo<? extends Base<String>> foo1 = new Foo<>(ex);  // ok
             <error descr="Incompatible types. Found: 'MyTest.Foo<MyTest.A>', required: 'MyTest.Foo<MyTest.Base<java.lang.String>>'">Foo<Base<String>> foo2 = new Foo<>(ex);</error>  // should be error
         }
     }
}

class NonParameterized {
  void foo() {
    new NonParameterized<<error descr="Diamond operator is not applicable for non-parameterized types"></error>>();
  }
}


interface I<T> {
  T m();
}

class FI1 {
  I<? extends String> i1 = new I<>() {
    @Override
    public String m() {
      return null;
    }
  };

  I<?> i2 = new I<>() {
    @Override
    public Object m() {
      return null;
    }
  };
}

class Super<X,Y> {
    private Super(Integer i, Y y, X x) {}
    public Super(Number n, X x, Y y) {}
}

class TestMySuper {
    Super<String,Integer> ssi1 = new Super<>(1, "", 2);
}

class TestLocal<X> {
    class Member { }
    static class Nested {}

    void test() {
        class Local {}

        Member m = new Member<<error descr="Diamond operator is not applicable for non-parameterized types"></error>>();
        Nested n = new Nested<<error descr="Diamond operator is not applicable for non-parameterized types"></error>>();
        Local l = new Local<<error descr="Diamond operator is not applicable for non-parameterized types"></error>>();
    }
}

class QualifiedTest {
  java.util.Map<String, String> s = new java.util.HashMap<>();
}


class TZ {

}

class ParenthTest<T extends TZ> {
    public ParenthTest(T x) {

    }

    public T z = null;

    public int a() {
        ParenthTest<T> x = (new ParenthTest<>(null)); //red code is here
        return 1;
    }
}