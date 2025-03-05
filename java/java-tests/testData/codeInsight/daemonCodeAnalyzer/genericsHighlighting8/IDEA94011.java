import java.util.*;
class Test {

    class Parent { }

    interface Consumer<T> { }

    interface MyConsumer<T extends Parent> extends Consumer<T> { }


    public void test(Set<MyConsumer> set) {
        @SuppressWarnings("unchecked")
        Map<Parent, MyConsumer<Parent>> map = <error descr="Incompatible types. Found: 'java.util.Map<java.lang.Object,Test.MyConsumer>', required: 'java.util.Map<Test.Parent,Test.MyConsumer<Test.Parent>>'">create</error>(set);

    }

    public <S, T extends Consumer<S>> Map<S, T> create(Set<T> consumers) {
        return null;
    }

}
