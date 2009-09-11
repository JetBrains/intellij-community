public class Builder<T> {
    private T t;

    public Builder setT(T t) {
        this.t = t;
        return this;
    }

    public Test createTest() {
        return new Test(t);
    }
}