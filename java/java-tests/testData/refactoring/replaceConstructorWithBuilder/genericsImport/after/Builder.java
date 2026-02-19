import java.io.Serializable;

public class Builder<T extends Serializable> {
    private String name;
    private Class<T> clazz;

    public Builder<T> setName(String name) {
        this.name = name;
        return this;
    }

    public Builder<T> setClazz(Class<T> clazz) {
        this.clazz = clazz;
        return this;
    }

    public Test<T> createTest() {
        return new Test<>(name, clazz);
    }
}