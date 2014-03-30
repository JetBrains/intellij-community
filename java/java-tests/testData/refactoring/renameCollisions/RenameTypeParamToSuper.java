abstract class A{
    class T{}
    abstract T foo();
}
class B<<caret>S> extends A{
    void foo(T x){}

    @Override
    T foo() {
        return null;
    }
}