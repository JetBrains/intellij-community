class ReturnTypeIncompatibility {

    interface I1<T extends Number> {
        T m(Integer x);
    }

    interface I2<L extends String> {
        L m(Integer x);
    }

    interface I3<K> {
        void m(Integer x);
    }

    static <P extends Number> void call(I1<P> i1) {
        i1.m(1);
    }

    static <P extends String> void call(I2<P> i2) {
        i2.m(2);
    }

    static <Q> void call(I3<Q> i3) {
        i3.m(3);
    }

    public static void main(String[] args) {
        <error descr="Ambiguous method call: both 'ReturnTypeIncompatibility.call(I1<Integer>)' and 'ReturnTypeIncompatibility.call(I2<P>)' match">call</error>(i-> {return i;});
    }
}


class ReturnTypeCompatibility {

    interface I1<T extends Number> {
        T m(T x);
    }

    interface I2<L extends String> {
        L m(L x);
    }

    interface I3<K> {
        void m(K x);
    }

    static <P extends Number> void call(I1<P> i1) {
        i1.m(null);
    }

    static <P extends String> void call(I2<P> i2) {
        i2.m(null);
    }

    static <Q> void call(I3<Q> i3) {
        i3.m(null);
    }

    public static void main(String[] args) {
        <error descr="Ambiguous method call: both 'ReturnTypeCompatibility.call(I1<Number>)' and 'ReturnTypeCompatibility.call(I2<P>)' match">call</error>(i-> {return i;});
    }
}

class ReturnTypeChecks1 {

    interface I<K extends Number, V extends Number> {
       V m(K k);
    }

    I<Integer, Integer> accepted = i -> { return i; };
    I<Double, Integer> rejected = i -> { return <error descr="Bad return type in lambda expression: Double cannot be converted to Integer">i</error>; };
}
