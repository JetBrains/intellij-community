class A<T> {}
class B<T> extends A<T> {}

class C {
    <T> B<T> f() {return null;}

    void bar () {
        A<String> a = <ref>f();
    }
}