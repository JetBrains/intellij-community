interface I {
  void m(int i);
}
interface J {
  void mm(int i, int j);
}
interface K {
  void k(String m);
}

class Foo {
  void foo(I i){}
  void foo(J j){}
  void foo(K k){}

  void bar() {
    foo<error descr="Ambiguous method call: both 'Foo.foo(I)' and 'Foo.foo(K)' match">((p) -> {
      System.out.println<error descr="Cannot resolve method 'println(<lambda parameter>)'">(p)</error>;
    })</error>;

    foo((p, k) -> {
      System.out.println(p);
    });

    foo((String s) ->{
      System.out.println(s);
    });

    <error descr="Cannot resolve method 'foo(<lambda expression>)'">foo</error>((String p, String k) -> {
      System.out.println(p);
    });
  }
}

class WithTypeParams {
  interface I<T> {
    void m(T t);
  }

  interface J<K, V> {
    void n(K k, V v);
  }

  class Foo {
    void foo(I<String> i){}
    void foo(J<String, String> j){}
    
    void bar() {
      foo((p) -> {
        System.out.println(p);
      });
  
      foo((p, k) -> {
        System.out.println(p);
      });
  
      foo((String s) ->{
        System.out.println(s);
      });
  
      foo((String p, String k) -> {
        System.out.println(p);
      });
  
      <error descr="Cannot resolve method 'foo(<lambda expression>)'">foo</error>((int k) -> {System.out.println(k);});
    }
  }
}