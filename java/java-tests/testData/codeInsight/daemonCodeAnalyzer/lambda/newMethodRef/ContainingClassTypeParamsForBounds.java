import java.util.List;

class Test {

    interface Function<X, Y> {
        Y m(X x);
    }

    interface Node<E> {
        List<E> foo();
    }

    class Data<T, I> {
        Data(I state, Function<I, List<T>> fun) { }
    }

    <O> Data<O, Node<O>> test(Node<O> collection) {
        return new Data<>(collection, Node::foo);
    }
}