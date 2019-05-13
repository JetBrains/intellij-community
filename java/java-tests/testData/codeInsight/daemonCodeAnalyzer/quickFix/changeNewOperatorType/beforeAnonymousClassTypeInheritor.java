// "Change 'new FooImpl<short[]>() {...}' to 'new Boo.FooImpl<String>()'" "true"

class Boo {
    abstract class Foo<T>{}
    abstract class FooImpl<K> extends Foo<K>{}
    
    private Foo<String> foo()
    {
        return new F<caret>ooImpl<short[]>() {};
    }
}

