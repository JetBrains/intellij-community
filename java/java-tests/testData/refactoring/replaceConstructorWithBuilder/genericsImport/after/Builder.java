import java.io.Serializable;

public class Builder<T extends Serializable> {
    private String name;
    private Class<T> clazz;

    public Builder setName(String name) {
        this.name = name;
        return this;
    }

    public Builder setClazz(Class<T> clazz) {
        this.clazz = clazz;
        return this;
    }

    public Test createTest() {
        return new Test(name, clazz);
    }
}