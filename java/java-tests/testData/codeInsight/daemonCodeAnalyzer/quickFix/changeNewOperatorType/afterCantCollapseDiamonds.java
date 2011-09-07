// "Change 'new Foo<Integer>(...)' to 'new Foo<Number>()'" "true"

class Foo<T> {
        Foo(T t) {}
        Foo() {}
}

class Constructors {
        public static void main(String[] args) {
                Foo<Number> foo2 = new Foo<Number>(1);
        }
}
