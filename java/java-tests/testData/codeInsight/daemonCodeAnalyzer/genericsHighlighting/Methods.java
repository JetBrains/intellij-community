import java.util.*;

interface Base {
}

class Derived implements Base {
}

class X {
    void method(int i, Base b) { }
    void method(int i, Derived b) { }

    {
        Derived d = new Derived();
        method(10, d);
    }
}

class Temp<T> {}

class A {
   <error descr="'A(T)' clashes with 'A(T)'; both methods have same erasure">public <T extends Temp<String>> A(T list)</error> {}
   public <T extends Temp<Integer>> A(T list) {}
}
class B {
   public <T extends A> B(T list) {}
   public <T extends Temp<Integer>> B(T list) {}
}



//////////////////////////////////////////
class IdeaBug {

    static <T> T cloneMe(T arg) throws CloneNotSupportedException {
        return (T) arg.<error descr="'clone()' has protected access in 'java.lang.Object'">clone</error>();
    }
}
