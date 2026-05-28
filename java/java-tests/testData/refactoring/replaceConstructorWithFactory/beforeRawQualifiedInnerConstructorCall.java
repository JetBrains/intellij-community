public class Outer {
    public class GenericClass<T> {
        public GenericClass<caret>(Class<T> type) { }
    }
}

class Usages {
    void m(Class<?> type, Outer owner) {
        Outer.GenericClass<Object> var1 = owner.new GenericClass(type);
        Outer.GenericClass<String> var2 = owner.new GenericClass(type);
        Outer.GenericClass<?> var3 = owner.new GenericClass(type);
        Outer.GenericClass var4 = owner.new GenericClass(type);
        Object var5 = owner.new GenericClass(type);
        owner.new GenericClass(type);
    }
}
