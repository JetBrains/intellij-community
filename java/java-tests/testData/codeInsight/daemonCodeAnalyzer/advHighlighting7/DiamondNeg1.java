class Neg01<X extends Number> {

    Neg01(X x) {
    }

    <Z> Neg01(X x, Z z) {
    }

    void test() {
        Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n1 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("" ); //new Foo<Integer> created
        Neg01<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n2 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Neg01<?> n3 = new Neg01<><error descr="'Neg01(? extends java.lang.Number)' in 'Neg01' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
        Neg01<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n4 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

        Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n5 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Neg01<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n6 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Neg01<?> n7 = new Neg01<><error descr="'Neg01(? extends java.lang.Number)' in 'Neg01' cannot be applied to '(java.lang.String)'">("")</error> {
        }; //new Foo<Object> created
        Neg01<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n8 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Object> created

        Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n9 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Neg01<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n10 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Neg01<?> n11 = new Neg01<><error descr="'Neg01(? extends java.lang.Number, java.lang.String)' in 'Neg01' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
        <error descr="Cannot resolve symbol 'Foo'">Foo</error><? super String> n12 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

        Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> n13 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Neg01<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> n14 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Neg01<?> n15 = new Neg01<><error descr="'Neg01(? extends java.lang.Number, java.lang.String)' in 'Neg01' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error> {
        }; //new Foo<Object> created
        Neg01<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> n16 = new Neg01<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Object> created
    }
}
