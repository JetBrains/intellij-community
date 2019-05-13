
class A<T>{
    class B{}
}

class C<T extends Throwable> extends A<T> {
    void foo(C<?>.B b){ bar<error descr="'bar(A<T>.B)' in 'C' cannot be applied to '(A<capture<?>>.B)'">(b)</error>; }
    <T extends Throwable> void bar(A<T>.B b){}
}