class Neg04 {

    void test() {
        class Foo<V extends Number> {
            Foo(V x) {}
            <Z> Foo(V x, Z z) {}
        }
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n1 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n2 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<?> n3 = new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n4 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n5 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n6 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
        Foo<?> n7 = new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Foo' cannot be applied to '(java.lang.String)'">("")</error>{}; //new Foo<Object> created
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n8 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n9 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n10 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<?> n11 = new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n12 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n13 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n14 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
        Foo<?> n15 = new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>{}; //new Foo<Object> created
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n16 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Object> created
    }
}
