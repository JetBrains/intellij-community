import java.util.*;
class Test {

    class Parent { }

    interface Consumer<T> { }

    interface MyConsumer<T extends Parent> extends Consumer<T> { }


    public void test(Set<MyConsumer> set) {
        @SuppressWarnings("unchecked")
        Map<Parent, MyConsumer<Parent>> map = create<error descr="'create(java.util.Set<T>)' in 'Test' cannot be applied to '(java.util.Set<Test.MyConsumer>)'">(set)</error>;

    }

    public <S, T extends Consumer<S>> Map<S, T> create(Set<T> consumers) {
        return null;
    }

}
