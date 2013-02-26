class DefaultConstructor {
    
    interface I1<R> {
        R invoke();
    }

    interface I2<R, A> {
        R invoke(A a);
    }
    
    static class Outer {
        class Inner {
        }
        
        static void test1() {
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,DefaultConstructor.Outer>'">I2<Inner, Outer> i2 = Inner :: new;</error>
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Inner, String> i2str = Inner :: new;</error>
        }
        
        void test2() {
            I1<Inner> i1 = Inner :: new;
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I1<java.lang.Integer>'">I1<Integer> i1Int = Inner :: new;</error>
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,DefaultConstructor.Outer>'">I2<Inner, Outer> i2 =  Inner :: new;</error>
        }
    }
    
    static void test1() {
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,DefaultConstructor.Outer>'">I2<Outer.Inner, Outer> i2 = Outer.Inner::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
    
    void test2() {
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,DefaultConstructor.Outer>'">I2<Outer.Inner, Outer> i2 = Outer.Inner::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
}

class DefaultConstructor1 {

    public void bar() {
    }

    {
        Runnable b1 = DefaultConstructor1 :: new;
    }
}

class DefaultConstructor2 {
    interface I {
        void foo(DefaultConstructor2 e);
    }


    void f() {
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor2.I'">I i1 = DefaultConstructor2 :: new;</error>
        I i2 = <error descr="Cannot find class this">this</error>::new;
    }
}

class DefaultConstructor3 {
   public class Inner {}
   public static class StaticInner {}
   
   static <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor3.I'">I i = Inner::new;</error>
   static I1 i1 = StaticInner::new;
   interface I {
     Inner foo();
   }

   interface I1 {
     StaticInner foo();
   }
}

class DefaultConstructor4 {
   public class Inner {}
   public static class StaticInner {}
   
   static <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor4.I'">I i = Inner::new;</error>
   static <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor4.I1'">I1 i1 = StaticInner::new;</error>
   interface I {
     Inner foo(DefaultConstructor4 receiver);
   }

   interface I1 {
     StaticInner foo(DefaultConstructor4 receiver);
   }
}

class DefaultConstructor5 {
    public class Inner {}

    static void test() {
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor5.I'">I i = Inner::new;</error>
    }

    void test1() {
        I i = Inner::new;
    }

    interface I {
        DefaultConstructor5.Inner foo();
    }

}
