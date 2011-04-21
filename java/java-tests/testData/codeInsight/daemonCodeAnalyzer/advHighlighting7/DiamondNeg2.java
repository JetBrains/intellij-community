class Neg02 {

    static class Foo<X extends Number> {
        Foo(X x) {}
        <Z> Foo(X x, Z z) {}
    }

    void testSimple() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<?> f3 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<?> f7 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
    }

    void testQualified() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<?> f3 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("");

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<?> f7 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Neg02.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", "");
    }
}
