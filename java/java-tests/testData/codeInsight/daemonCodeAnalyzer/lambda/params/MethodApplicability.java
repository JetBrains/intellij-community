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
    <error descr="Ambiguous method call: both 'Foo.foo(I)' and 'Foo.foo(K)' match">foo</error>((p) -> {
      System.out.println<error descr="Cannot resolve method 'println(<lambda parameter>)'">(p)</error>;
    });

    foo((p, k) -> {
      System.out.println(p);
    });

    foo((String s) ->{
      System.out.println(s);
    });

    foo(<error descr="Incompatible parameter types in lambda expression: expected int but found String">(String p, String k)</error> -> {
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
  
      foo(<error descr="Incompatible parameter types in lambda expression: expected String but found int">(int k)</error> -> {System.out.println(k);});
    }
  }
}