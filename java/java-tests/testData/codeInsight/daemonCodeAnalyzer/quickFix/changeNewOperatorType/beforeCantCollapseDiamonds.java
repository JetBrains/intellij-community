// "Change 'new Foo<Integer>(...)' to 'new Foo<Number>()'" "true-preview"

class Foo<T> {
        Foo(T t) {}
        Foo() {}
}

class Constructors {
        public static void main(String[] args) {
                Foo<Number> foo2 = new Foo<Int<caret>eger>(1);
        }
}
