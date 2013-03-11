class MyTest {
    
    static class Foo<T> {
        T m() { return null; };
    }
    
    interface I1 {
        Foo<Object> m(Foo<String> f);
    }

    interface I2 {
        Integer m(Foo<Integer> f);
    }
    
    interface I3 {
        Object m(Foo<Integer> f);
    }
    
    private static void <warning descr="Private method 'foo(MyTest.I1)' is never used">foo</warning>(I1 i) {
      System.out.println(i);
    }

    private static void foo(I2 i) {  
      System.out.println(i);
    }

    private static void <warning descr="Private method 'foo(MyTest.I3)' is never used">foo</warning>(I3 i) {
      System.out.println(i);
    }

    public static void main(String[] args) {
        foo(Foo::m);
    }
}

class MyTest1 {
    
    interface I1 {
       Foo<?> m(String s);
    }


    interface I2 {
        Foo<?> m(Integer i);
    }
    
    interface I3 {
        Foo<Number> m(Integer i);
    }

    static class Foo<T extends Number> {
        Foo(T t) {
          System.out.println(t);
        }
    }

    private static void <warning descr="Private method 'm(MyTest1.I1)' is never used">m</warning>(I1 i) {
        System.out.println(i);
    }

    private static void <warning descr="Private method 'm(MyTest1.I2)' is never used">m</warning>(I2 i) {
        System.out.println(i);
    }

    private static void m(I3 i) {
        System.out.println(i);
    }

    public static void main(String[] args) {
        m(Foo::new);
    }
}
class MyTest2 {
    
    interface I1 {
       Foo<?> m(String s);
    }


    interface I2 {
        Foo<Integer> m(Integer i);
    }
    
    interface I3 {
        Foo<Number> m(Integer i);
    }

    static class Foo<T extends Number> {
        Foo(T t) {
          System.out.println(t);
        }
    }

    private static void <warning descr="Private method 'm(MyTest2.I1)' is never used">m</warning>(I1 i) {
        System.out.println(i);
    }

    private static void m(I2 i) {
        System.out.println(i);
    }

    private static void <warning descr="Private method 'm(MyTest2.I3)' is never used">m</warning>(I3 i) {
        System.out.println(i);
    }

    public static void main(String[] args) {
        m(Foo::new);
    }
}
