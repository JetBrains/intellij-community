// "Change 'new FooImpl<short[]>() {...}' to 'new Boo.FooImpl<String>()'" "true-preview"

class Boo {
    abstract class Foo<T>{}
    abstract class FooImpl<K> extends Foo<K>{}
    
    private Foo<String> foo()
    {
        return new FooImpl<String>() {
        };
    }
}

