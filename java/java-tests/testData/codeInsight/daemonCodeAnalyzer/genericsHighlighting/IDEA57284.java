abstract class A<S> {
    abstract <T extends S> void foo();
    void bar(A<? super Exception> x){
        x.<<error descr="Type parameter 'String[]' is not within its bound; should extend 'capture<? super java.lang.Exception>'">String[]</error>>foo();
    }
}