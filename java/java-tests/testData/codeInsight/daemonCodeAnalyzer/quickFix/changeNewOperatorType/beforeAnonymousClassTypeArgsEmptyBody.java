// "Change 'new Foo<short[]>() {...}' to 'new Boo.Foo<String>()'" "true-preview"

class Boo {
    abstract class Foo<T>{}
    
    private Foo<String> foo()
    {
        return new F<caret>oo<short[]>() {};
    }
}

