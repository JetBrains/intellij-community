package pck;
import static pck.D.foo;
import static pck.C.foo;

class C {
     public static <T> String foo(Comparable<? extends Comparable<T>> x){
         return null;
     }
}

class D {
    public static <T> void foo(Comparable<? extends T> x){}
}

class B{
    public static void bar(){
        foo(1).toLowerCase();
    }
}