class Neg04 {

    void test() {
        class Foo<V extends Number> {
            Foo(V x) {}
            <Z> Foo(V x, Z z) {}
        }
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n1 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n2 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<?> n3 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n4 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>;

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n5 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n6 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<?> n7 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n8 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
    }
}
