public class Test extends B<S<caret>tring>{
    String foo() {
        return null;
    }

    void bar() {
        if (foo() == null) {}
    }
}

abstract class A<T> {
    abstract T foo();
}

abstract class B<E extends Object> extends A<E> {
}


