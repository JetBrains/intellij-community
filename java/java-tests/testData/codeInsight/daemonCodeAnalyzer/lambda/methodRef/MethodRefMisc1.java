import java.util.*;
class MyTest {

   static void m(int i) {//here 
   }
   static void m(Integer i) {}

   interface I {
       void m(int x);
   }

   static void call(I s) { s.m(42); }

   public static void main(String[] args) {
       I s = MyTest::m;
       s.m(42);
       call(MyTest::m);
   }
}

class MyTest1 {

    static void m(Integer i) { }

    interface I1 {
        void m(int x);
    }

    interface I2 {
        void m(Integer x);
    }

    static void call(int i, I1 s) {
      
    }
    static void call(int i, I2 s) {
      //here
    }

    public static void main(String[] args) {
        call<error descr="Ambiguous method call: both 'MyTest1.call(int, I1)' and 'MyTest1.call(int, I2)' match">(1, MyTest1::m)</error>;
    }
}

class MyTest2 {

    static void m(Integer i) { }

    interface I {
        void m(int x);
    }

    static void call(int i, I s) {   }
    static void call(Integer i, I s) {   }

    static void test() {
        call(1, MyTest2::m); //call(int i, I s)
    }
}

class MyTest3 {
    interface I {
       void m();
    }

    MyTest3() {}

   static void m() { }

   public static void main(String[] args) {
      I s = <error descr="Static method referenced through non-static qualifier">new MyTest3()::m</error>;
   }
}

class MyTest4 {

    interface I {
        MyTest4 m(List<Integer> l1, List<Integer> l2);
    }

    MyTest4 meth(List<Integer>... lli) { return null; }
    MyTest4(List<Integer>... lli) { }

    I s1 = this::meth;
    I s2 = MyTest4::new;
}

class MyTest5 {
    interface I_void<X> {
        void m();
    }
    
    interface I_Void<X> {
        void m();
    }
    
    static void m_void() {}
    
    static Void _Void() {return null; }
    
    public static void main(String[] args) {
        I_void s1 = MyTest5::m_void;
        s1.m();
        I_Void s2 = MyTest5::m_void;
        s2.m();
        I_void s3 = MyTest5::_Void;
        s3.m();
        I_Void s4 = MyTest5::_Void;
        s4.m();
    }
}
class MyTest6 {
    interface I {
        MyTest6 invoke();
    }
    
    MyTest6() {
    }
    
    static MyTest6 m() {
        return null;
    }

    public static void main(String[] args) {
        I I1 = ((I)() -> {return null; })::invoke;
        I1.invoke();
        I I2 = ((I)MyTest6::new)::invoke;
        I1.invoke();
        I I3 = ((I)MyTest6::m)::invoke;
        I1.invoke();
    }
}

class MyTest7{
    
    interface I<R> {
        R invoke();
    }
    
    @interface A { }

    static abstract class AC { }
    
    enum E { }
    
    void test() {
        I s1 = <error descr="'A' is abstract; cannot be instantiated">A::new</error>;
        I s2 = <error descr="'I' is abstract; cannot be instantiated">I::new</error>;
        I s3 = <error descr="'AC' is abstract; cannot be instantiated">AC::new</error>;
        I s4 = <error descr="Enum types cannot be instantiated">E::new</error>;
    }
}

class MyTest8{

    static class Sup {} 


    static class Sub extends Sup {

        interface I { Sup m(Sup x, String str); }

        class Inner extends Sup {
            Inner(String val) { }
        }

        void test() {
            I var = Sub.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;;
        }
    }
}

class MyTest9 {
    
    static class SuperFoo<X> { }

    static class Foo<X extends Number> extends SuperFoo<X> { }
    
    interface I1 {
        void m();
    }

    interface I2 {
        void m();
    }
    
    static <X extends Number> Foo<X> m() { return null; }
    
    static void g1(I1 s) { }
    static void g2(I1 s) { }
    static void g2(I2 s) { }

    void test() {
        g1(MyTest9::m);
        g2<error descr="Ambiguous method call: both 'MyTest9.g2(I1)' and 'MyTest9.g2(I2)' match">(MyTest9::m)</error>;
    }
}
