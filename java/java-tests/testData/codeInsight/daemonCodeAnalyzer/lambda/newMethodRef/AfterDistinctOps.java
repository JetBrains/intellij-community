



class BBB {

    static <T> void f() {
        TerminalOp<T, LinkedHashSet<T>> <warning descr="Variable 'reduceOp' is never used">reduceOp</warning> = BBB.<T, LinkedHashSet<T>>makeRef(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    public static <T, U> TerminalOp<T, U> makeRef(U <warning descr="Parameter 'seed' is never used">seed</warning>, BiFunction<U, ? super T, U> <warning descr="Parameter 'reducer' is never used">reducer</warning>, BinaryOperator<U> <warning descr="Parameter 'combiner' is never used">combiner</warning>) {
        return null;
    }
    public static <T, R> TerminalOp<T, R> makeRef(Supplier<R> <warning descr="Parameter 'seedFactory' is never used">seedFactory</warning>, BiConsumer<R, ? super T> <warning descr="Parameter 'accumulator' is never used">accumulator</warning>, BiConsumer<R,R> <warning descr="Parameter 'reducer' is never used">reducer</warning>) {
        return null;
    }

    public static void main(String[] args) {
        f();
    }
}
interface Supplier<T> {

    T get();
}
interface BiFunction<T, U, R> {
    R apply(T t, U u);
}

interface BinaryOperator<T> extends BiFunction<T,T,T> {}
interface BiConsumer<T, U> {
    void accept(T t, U u);
}
interface TerminalOp<<warning descr="Type parameter 'E_IN' is never used">E_IN</warning>, <warning descr="Type parameter 'R' is never used">R</warning>> {}
class LinkedHashSet<E> extends HashSet<E>{
    public LinkedHashSet(int <warning descr="Parameter 'initialCapacity' is never used">initialCapacity</warning>, float <warning descr="Parameter 'loadFactor' is never used">loadFactor</warning>) {
    }


    public LinkedHashSet(int <warning descr="Parameter 'initialCapacity' is never used">initialCapacity</warning>) {
    }

    public LinkedHashSet() {
    }


}

class HashSet<E> extends AbstractSet<E> {
    public HashSet() {
    }

    public HashSet(int <warning descr="Parameter 'initialCapacity' is never used">initialCapacity</warning>, float <warning descr="Parameter 'loadFactor' is never used">loadFactor</warning>) {
    }

    public HashSet(int <warning descr="Parameter 'initialCapacity' is never used">initialCapacity</warning>) {
    }
    HashSet(int <warning descr="Parameter 'initialCapacity' is never used">initialCapacity</warning>, float <warning descr="Parameter 'loadFactor' is never used">loadFactor</warning>, boolean <warning descr="Parameter 'dummy' is never used">dummy</warning>) {
    }

    public boolean add(E e) {
        return true;
    }

}

abstract class AbstractSet<E> extends AbstractCollection<E> {
    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected AbstractSet() {
    }
}

abstract class AbstractCollection<E> implements Collection<E> {
    public boolean add(E e) {
        return true;
    }
    public boolean addAll(Collection<? extends E> c) {
        boolean modified = false;
        return modified;
    }
}

interface Collection<<warning descr="Type parameter 'E' is never used">E</warning>> {}
