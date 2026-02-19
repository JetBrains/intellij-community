public class Builder<T> {
    private T t;

    public Builder<T> setT(T t) {
        this.t = t;
        return this;
    }

    public Test<T> createTest() {
        return new Test<>(t);
    }
}