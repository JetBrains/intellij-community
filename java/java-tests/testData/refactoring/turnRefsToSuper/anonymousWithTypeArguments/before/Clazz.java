interface Intf {
}

interface Factory<E extends Intf> {
    E create();
    void save(E obj);

    static class helper {
        static Factory<Intf> get2() {
            return (Factory)new Factory<Clazz>() {
                public Clazz create() { return null; }
                public void save(Clazz obj) { }
            };
        }
    }
}


public class Clazz implements Intf {
}
