public class Wrapper<T> {
    private final T value;

    public Wrapper(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}
