import java.util.*;
class Test {

    class Parent { }

    interface Consumer<T> { }

    interface MyConsumer<T extends Parent> extends Consumer<T> { }


    public void test(Set<MyConsumer> set) {
        @SuppressWarnings("unchecked")
        Map<Parent, MyConsumer<Parent>> map = <error descr="Inferred type 'T' for type parameter 'T' is not within its bound; should implement 'Test.Consumer<Test.Parent>'">create(set)</error>;

    }

    public <S, T extends Consumer<S>> Map<S, T> create(Set<T> consumers) {
        return null;
    }

}
