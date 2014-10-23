import java.util.Collection;
abstract class A1 {
    abstract <TValue> void foo(Collection<T> valueCollection);
}

class B1 extends A1 {
    @Override
    <TValue> void foo(Collection<T> valueCollection) {
        <caret>
    }
}
