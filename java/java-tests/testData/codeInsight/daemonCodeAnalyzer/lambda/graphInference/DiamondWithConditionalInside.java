
import java.util.function.Predicate;

class Foo<T> {
    public Foo(Predicate<T> p) {
    }

    void m(Predicate<String> p){
         new Foo<>(p == null ? null : acc -> p.test<error descr="'test(java.lang.String)' in 'java.util.function.Predicate' cannot be applied to '(java.lang.Object)'">(acc)</error>);
         new Foo<>(acc -> p.test<error descr="'test(java.lang.String)' in 'java.util.function.Predicate' cannot be applied to '(java.lang.Object)'">(acc)</error>);
    }
}