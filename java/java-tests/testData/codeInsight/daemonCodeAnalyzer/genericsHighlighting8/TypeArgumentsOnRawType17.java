interface I{
    <T extends Iterable<String>> void foo();
}

abstract class A<S> implements I {
    public abstract <T extends Iterable<String>> void foo();
    <T extends A> void bar(T x){
        A a = null;
        a.<Iterable<String>> foo();
        x.<Iterable<String>> foo();
    }
}

abstract class B<S> {
    public abstract <T extends Iterable<String>> void foo();
    <T extends B> void bar(T x){
        B a = null;
        a.<Iterable<String>> foo();
        x.<Iterable<String>> foo();
    }
}

abstract class C<S> {
    public abstract <T extends Iterable<String>> void foo();
    <T extends C & I> void bar(T x){
        x.<Iterable<String>> foo();
    }
}

//---------------------------------------------------------------
interface I1 {
    void foo();
}


abstract class B1<S> {
    public abstract <T extends Iterable<String>> void foo();
    <T extends B1 & I1> void bar(T x){
        B1 a = null;
        a.<Iterable<String>> foo();
        x.<Iterable<String>> foo();
    }
}
