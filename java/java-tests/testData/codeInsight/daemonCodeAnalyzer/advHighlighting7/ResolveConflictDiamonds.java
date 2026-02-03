class Test {
    enum FooBar {Foo, Bar}

    void someMethod() {
        new Infer<>((FooBar) null, FooBar.class);
        new Infer<FooBar>( (FooBar) null, FooBar.class );
    }


    public class Infer<T extends Enum<T>> {
        @SafeVarargs
        public Infer(T inst, Class<T> tClass, T... excludes) {
        }

        @SafeVarargs
        public Infer(String inst, Class<T> tClass, T... excludes) {
        }
    }
}