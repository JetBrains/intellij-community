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
            I2<Inner, Outer> i2 = Inner :: new;
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Inner, String> i2str = Inner :: new;</error>
        }
        
        void test2() {
            I1<Inner> i1 = Inner :: new;
            <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I1<java.lang.Integer>'">I1<Integer> i1Int = Inner :: new;</error>
            I2<Inner, Outer> i2 =  Inner :: new;
        }
    }
    
    static void test1() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
    
    void test2() {
        I2<Outer.Inner, Outer> i2 = Outer.Inner::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'DefaultConstructor.I2<DefaultConstructor.Outer.Inner,java.lang.String>'">I2<Outer.Inner, String> i2str = Outer.Inner::new;</error>
    }
}