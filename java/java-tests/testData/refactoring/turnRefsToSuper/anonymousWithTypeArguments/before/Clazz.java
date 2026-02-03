interface IntF {
}

interface Factory<E extends IntF> {
    E create();
    void save(E obj);

    static class helper {
        static Factory<IntF> get2() {
            return (Factory)new Factory<Clazz>() {
                public Clazz create() { return null; }
                public void save(Clazz obj) { }
            };
        }
    }
}


public class Clazz implements IntF {
}
