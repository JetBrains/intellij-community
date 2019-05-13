import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;

class Main {
    public static void main(String[] args) {
        FlatMap<?, Item> flatmap = new FlatMap<>();
        Col<String> s1 = new Col<>();
        Col<Item> s2 = s1.apply(flatmap.with((String word) -> singletonList(new Item())));
        Col<Item> s22 = s1.apply(flatmap.with((word) -> singletonList(new Item())));
    }
}

class FlatMap<I, O> implements Transform<Col<I>, Col<O>> {

    <I2> FlatMap<I2, O> with(Function<I2, List<O>> fn) {
        return new FlatMap<>();
    }
}

class Col<E> {

    <R> Col<R> apply(Transform<Col<E>, Col<R>> transform) {
        return new Col<>();
    }
}

interface Transform<I, O> { }

class Item { }