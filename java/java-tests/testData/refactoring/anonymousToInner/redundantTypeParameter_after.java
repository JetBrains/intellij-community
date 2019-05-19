
import java.util.function.Consumer;

class AnonymToInnerWIthGeneric<T> {
    private final Consumer<T> our = System.out::println;

    private final Consumer<T> my = new MyConsumer();

    private class MyConsumer implements Consumer<T> {
        @Override
        public void accept(T t) {
            our.accept(t);
        }
    }
}