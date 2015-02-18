class MyTest {
    
    static class Foo<T> {
        T m() { 
          return null; 
        }
    }
    
    interface I {
        Integer m(Foo<Integer> f);
    }

    public static void main(String[] args) {
        I i = Foo::m;
    }
}

class MyTest1 {
    
    interface I1 {
       void m(String s);
    }

    interface I2 {
       void m(Integer i);
    }
    
    interface I3 {
       void m(Object o);
    }

    static class Foo<T extends Number> {
        Foo(T t) {}
    }

 
    static void foo(I1 i) {}
    static void foo(I2 i) {}
    static void foo(I3 i) {}

    static {
        foo<error descr="Ambiguous method call: both 'MyTest1.foo(I1)' and 'MyTest1.foo(I2)' match">(Foo::new)</error>;
    }
}
