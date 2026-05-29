public class GenericClass<T> {
    public GenericClass<caret>(Class<T> type) { }
}

class Usages {
    void m(Class<?> type) {
        GenericClass<Object> var1 = new GenericClass(type);
        GenericClass<String> var2 = new GenericClass(type);
        GenericClass<String> var3 = new GenericClass(null);
        Class<String> stringType = String.class;
        GenericClass<String> var4 = new GenericClass(stringType);
        Class<Object> objectType = Object.class;
        GenericClass<Object> var5 = new GenericClass(objectType);
        GenericClass<?> var6 = new GenericClass(type);
        GenericClass var7 = new GenericClass(type);
        Object var8 = new GenericClass(type);
        acceptString(new GenericClass(null));
        new GenericClass(type);
    }

    <T> void m2(Class<T> type) {
        GenericClass<Object> var3 = new GenericClass(type);
        GenericClass<String> var4 = new GenericClass(type);
    }

    GenericClass<String> create() {
        return new GenericClass(null);
    }

    void acceptString(GenericClass<String> value) { }
}
