// "Create constructor" "true"
class MyTest {
    <T> T id(T t) {
        return t;
    }
    
    {
        Foo f = new Foo(id("name"));
    }
}

class Foo {
    public Foo() {}

    public Foo(String name) {
        
    }
}