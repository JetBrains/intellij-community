public class Test<A, B extends Number>  {
    interface IO<T> {
        T _(Object o);
    }

    interface IN<T extends Number> {
        T _(Object o);
    }


    private void foo(IO<? extends A> <warning descr="Parameter 'p' is never used">p</warning>) {}
    private void <warning descr="Private method 'foo(Test.IN<? extends B>)' is never used">foo</warning>(IN<? extends B> <warning descr="Parameter 'p' is never used">p</warning>) {}


    private static class Inner<A extends Object, B extends Number> {
        private void <warning descr="Private method 'm8(Test.IO<? extends A>)' is never used">m8</warning>(IO<? extends A> <warning descr="Parameter 'p' is never used">p</warning>) {}
        private void m8(IN<? extends B> <warning descr="Parameter 'p' is never used">p</warning>) {}
    }

    public static void main(String[] args) {
        Inner<Number, Double> inn = new Inner<>();
        inn.m8(p -> 1.0);
        new Test<Number, Integer>().foo(p -> 1.0);

    }
}

