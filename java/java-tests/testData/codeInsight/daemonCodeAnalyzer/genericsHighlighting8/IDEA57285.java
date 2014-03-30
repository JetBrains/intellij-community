package pck;

abstract class A<S> {
    S y;
    void bar(A<? extends int[]> x){
        Object obj = <error descr="Array type expected; found: 'capture<? extends int[]>'">x.y</error>[0];
    }

    void baz(A<? super int[]> x){
        Object obj = <error descr="Array type expected; found: 'capture<? super int[]>'">x.y</error>[0];
    }
}
