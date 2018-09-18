import java.util.function.Supplier;

class Foo {
    private final Singleton<B> singletonB = new Singleton<>(() -> f());
    static B f() {
        return null;
    }
}

interface B {
    String getData();
}

class Singleton<T>  {
    public Singleton(Supplier<T> supplier) { }
    public Singleton(T instance) { }
}