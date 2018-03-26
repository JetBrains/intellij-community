
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

class A<T extends A> {

    public A(){}
    public A(Collection<Integer> x, Consumer<? super T> y) {}

    public void failToCompile(Consumer<? super T> x) {}

    public A<T> add(A x) {
        return this;
    }

    public void fail(Collection x) {
        A<A> builder = new A<>();
        builder.add(new A<>(x, A::notify))
                .failToCompile(A::notify);
        builder.failToCompile(A::notify);
    }
}

abstract class B<K> {
    abstract List<String> add(B b);
    abstract <Z> B<Z> create(List<String> l, Consumer<?> c);

    void f(List l, B b) {
        String o  = add(create(l, (t) -> t.hashCode())).get(0);//fails to compile in java 8, compiles in java 9, 10
        String o1 = add(create(l, (t) -> {})).get(0);
        String o2 = add(b).get(0);
    }
}