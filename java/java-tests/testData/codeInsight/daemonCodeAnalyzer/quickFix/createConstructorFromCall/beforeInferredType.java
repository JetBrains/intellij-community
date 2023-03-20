// "Create constructor" "true-preview"
class MyTest {
    <T> T id(T t) {
        return t;
    }
    
    {
        Foo f = new Foo(i<caret>d("name"));
    }
}

class Foo {
    public Foo() {}
}