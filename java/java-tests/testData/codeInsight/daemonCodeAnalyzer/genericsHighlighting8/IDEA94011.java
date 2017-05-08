import java.util.*;
class Test {

    class Parent { }

    interface Consumer<T> { }

    interface MyConsumer<T extends Parent> extends Consumer<T> { }


    public void test(Set<MyConsumer> set) {
        @SuppressWarnings("unchecked")
        Map<Parent, MyConsumer<Parent>> map = <error descr="Incompatible types. Required Map<Parent, MyConsumer<Parent>> but 'create' was inferred to Map<S, T>:
Incompatible equality constraint: MyConsumer<Test.Parent> and MyConsumer">create(set);</error>

    }

    public <S, T extends Consumer<S>> Map<S, T> create(Set<T> consumers) {
        return null;
    }

}
