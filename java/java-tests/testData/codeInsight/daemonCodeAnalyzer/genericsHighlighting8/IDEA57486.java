class B<T,S>{}
abstract class A<T> {
    abstract B<T,T> foo();
    <T> void baz(B<T,T> b){}
    void bar(A<?> a){
        baz(a.foo());
    }
}
