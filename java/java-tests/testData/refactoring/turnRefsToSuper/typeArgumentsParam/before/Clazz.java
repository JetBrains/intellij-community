interface Intf {
}

interface Factory<E extends Intf> {
    E create();

    void save(E obj);

    static class helper {
        static Factory<Intf> get() {
            return null;
        }
    }
}


public class Clazz implements Intf {
    void bar() {
        Clazz y = Factory.helper.get().create();
        Factory.helper.get().save(y);
    }
}
