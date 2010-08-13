class Neg02 {

    static class Foo<X extends Number> {
        Foo(X x) {
        }

        <Z> Foo(X x, Z z) {
        }
    }

    void testSimple() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<?> f3 = new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg02.Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Foo<?> f7 = new Foo< ><error descr="'Foo(? extends java.lang.Number)' in 'Neg02.Foo' cannot be applied to '(java.lang.String)'">("")</error> {
        }; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f9 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f10 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Foo< ><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg02.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f12 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f13 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f14 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<?> f15 = new Foo< ><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg02.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error> {
        }; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f16 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Object> created
    }

    void testQualified() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
        Foo<?> f3 = new Neg02.Foo< ><error descr="'Foo(? extends java.lang.Number)' in 'Neg02.Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Integer> created
        Foo<?> f7 = new Neg02.Foo< ><error descr="'Foo(? extends java.lang.Number)' in 'Neg02.Foo' cannot be applied to '(java.lang.String)'">("")</error> {
        }; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("") {
        }; //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f9 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f10 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Neg02.Foo< ><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg02.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f12 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f13 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f14 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<?> f15 = new Neg02.Foo< ><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg02.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error> {
        }; //new Foo<Object> created
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f16 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "") {
        }; //new Foo<Object> created
    }
}

