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
            I2<Inner, Outer> i2 = Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
            I2<Inner, String> i2str = Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
        }
        
        void test2() {
            I1<Inner> i1 = Inner :: new;
            I1<Integer> i1Int = <error descr="Bad return type in method reference: cannot convert DefaultConstructor.Outer.Inner to java.lang.Integer">Inner :: new</error>;
            I2<Inner, Outer> i2 =  Inner :: <error descr="Cannot resolve constructor 'Inner'">new</error>;
        }
    }
    
    static void test1() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
        I2<Outer.Inner, String> i2str = Outer.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
    }
    
    void test2() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
        I2<Outer.Inner, String> i2str = Outer.Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
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
        I i1 = DefaultConstructor2 :: <error descr="Cannot resolve constructor 'DefaultConstructor2'">new</error>;
        I i2 = <error descr="Cannot find class this">this</error>::new;
    }
}

class DefaultConstructor3 {
   public class Inner {}
   public static class StaticInner {}
   
   static I i = Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
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
   
   static I i = Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
   static I1 i1 = StaticInner::<error descr="Cannot resolve constructor 'StaticInner'">new</error>;
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
        I i = Inner::<error descr="Cannot resolve constructor 'Inner'">new</error>;
    }

    void test1() {
        I i = Inner::new;
    }

    interface I {
        DefaultConstructor5.Inner foo();
    }

}
