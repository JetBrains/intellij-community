public class Outer {
    public <T> GenericClass<T> createGenericClass(Class<T> type) {
        return new GenericClass<T>(type);
    }

    public class GenericClass<T> {
        private GenericClass(Class<T> type) { }
    }
}

class Usages {
    void m(Class<?> type, Outer owner) {
        Outer.GenericClass<Object> var1 = (Outer.GenericClass) owner.createGenericClass(type);
        Outer.GenericClass<String> var2 = (Outer.GenericClass) owner.createGenericClass(type);
        Outer.GenericClass<?> var3 = owner.createGenericClass(type);
        Outer.GenericClass var4 = owner.createGenericClass(type);
        Object var5 = owner.createGenericClass(type);
        owner.createGenericClass(type);
    }
}
