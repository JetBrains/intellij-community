// "Change 'new Foo<short[]>() {...}' to 'new Boo.Foo<String>()'" "true"

class Boo {
    abstract class Foo<T>{}
    
    private Foo<String> foo()
    {
        return new Foo<String>() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
    }
}

