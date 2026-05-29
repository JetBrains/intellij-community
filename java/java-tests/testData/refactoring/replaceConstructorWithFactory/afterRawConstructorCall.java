public class GenericClass<T> {
    private GenericClass(Class<T> type) { }

    public static <T> GenericClass<T> createGenericClass(Class<T> type) {
        return new GenericClass<T>(type);
    }
}

class Usages {
    void m(Class<?> type) {
        GenericClass<Object> var1 = (GenericClass) GenericClass.createGenericClass(type);
        GenericClass<String> var2 = (GenericClass) GenericClass.createGenericClass(type);
        GenericClass<String> var3 = GenericClass.createGenericClass(null);
        Class<String> stringType = String.class;
        GenericClass<String> var4 = GenericClass.createGenericClass(stringType);
        Class<Object> objectType = Object.class;
        GenericClass<Object> var5 = GenericClass.createGenericClass(objectType);
        GenericClass<?> var6 = GenericClass.createGenericClass(type);
        GenericClass var7 = GenericClass.createGenericClass(type);
        Object var8 = GenericClass.createGenericClass(type);
        acceptString(GenericClass.createGenericClass(null));
        GenericClass.createGenericClass(type);
    }

    <T> void m2(Class<T> type) {
        GenericClass<Object> var3 = (GenericClass) GenericClass.createGenericClass(type);
        GenericClass<String> var4 = (GenericClass) GenericClass.createGenericClass(type);
    }

    GenericClass<String> create() {
        return GenericClass.createGenericClass(null);
    }

    void acceptString(GenericClass<String> value) { }
}
