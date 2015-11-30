public class Test<A, B extends Number>  {
    interface IO<T> {
        T _(Object o);
    }

    interface IN<T extends Number> {
        T _(Object o);
    }


    private void <warning descr="Private method 'foo(Test.IO<? extends A>)' is never used">foo</warning>(IO<? extends A> <warning descr="Parameter 'p' is never used">p</warning>) {}
    private void <warning descr="Private method 'foo(Test.IN<? extends B>)' is never used">foo</warning>(IN<? extends B> <warning descr="Parameter 'p' is never used">p</warning>) {}


    private static class Inner<A extends Object, B extends Number> {
        private void <warning descr="Private method 'm8(Test.IO<? extends A>)' is never used">m8</warning>(IO<? extends A> <warning descr="Parameter 'p' is never used">p</warning>) {}
        private void <warning descr="Private method 'm8(Test.IN<? extends B>)' is never used">m8</warning>(IN<? extends B> <warning descr="Parameter 'p' is never used">p</warning>) {}
    }

    public static void main(String[] args) {
        Inner<Number, Double> inn = new Inner<>();
        inn.<error descr="Ambiguous method call: both 'Inner.m8(IO<? extends Number>)' and 'Inner.m8(IN<? extends Double>)' match">m8</error>(p -> 1.0);
        new Test<Number, Integer>().<error descr="Ambiguous method call: both 'Test.foo(IO<? extends Number>)' and 'Test.foo(IN<? extends Integer>)' match">foo</error>(p -> 1.0);

    }
}

