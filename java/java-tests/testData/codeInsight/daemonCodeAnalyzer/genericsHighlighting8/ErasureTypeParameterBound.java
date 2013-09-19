interface Foo<T> {
    public <A extends T, B extends A> void bar(Class<A> key, B value);
}

class FooImpl implements Foo<String>{
     public <A extends String,  B extends A> void bar(Class<A> key, B value) {

    }
}
