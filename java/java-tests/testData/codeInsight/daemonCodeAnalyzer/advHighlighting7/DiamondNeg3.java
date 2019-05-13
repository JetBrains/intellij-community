class Neg03<U> {

    class Foo<V extends Number> {
        Foo(V x) {}
        <Z> Foo(V x, Z z) {}
    }

    void testSimple() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<?> f3 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<?> f7 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
    }

    void testQualified_1() {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<?> f3 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<?> f7 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Neg03<U>.Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
    }

    void testQualified_2(Neg03<U> n) {
        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<?> f3 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>;

        Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<?> f7 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
        Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = n.new Foo<><error descr="'Foo(java.lang.Number & java.lang.String, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>;
    }
}
