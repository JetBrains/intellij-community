import java.util.Collections;
import java.util.List;

class A<T> {}

class B<T> {
    B(List<A<?>> list) {}
}

class Bug {

    private static B case1(A<?> a) {
        return new B(Collections.singletonList(a));
    }

    private static B case2(A<A> a) {
        return new B(Collections.singletonList(a));
    }

    public static void main(String[] args) {
        System.out.println(case1(new A()));
        System.out.println(case2(new A<A>()));
    }
}