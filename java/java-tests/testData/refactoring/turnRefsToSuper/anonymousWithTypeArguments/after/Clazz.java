interface Intf {
}

interface Factory<E extends Intf> {
    E create();
    void save(E obj);

    static class helper {
        static Factory<Intf> get2() {
            return (Factory)new Factory<Intf>() {
                public Intf create() { return null; }
                public void save(Intf obj) { }
            };
        }
    }
}


public class Clazz implements Intf {
}
