interface IntF {
}

interface Factory<E extends IntF> {
    E create();

    void save(E obj);

    static class helper {
        static Factory<IntF> get() {
            return null;
        }
    }
}

public class Clazz implements IntF {
    void bar() {
        IntF y = Factory.helper.get().create();
        Factory.helper.get().save(y);
    }
}
