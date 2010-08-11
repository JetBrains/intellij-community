class Neg02 {

    static class Foo<X extends Number> {
        Foo(X x) {
        }

        <Z> Foo(X x, Z z) {
        }
    }

    void testSimple() {
        Foo<<error>String</error>> f1 = new Foo<<error></error>>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = new Foo<<error></error>>(""); //new Foo<Integer> created
        Foo<?> f3 = new Foo<><error>("")</error>; //new Foo<Object> created
        Foo<? super String> f4 = new Foo<<error></error>>(""); //new Foo<Object> created

        Foo<<error>String</error>> f5 = new Foo<<error></error>>("") {
        }; //new Foo<Integer> created
        Foo<? extends String> f6 = new Foo<<error></error>>("") {
        }; //new Foo<Integer> created
        Foo<?> f7 = new Foo< ><error>("")</error> {
        }; //new Foo<Object> created
        Foo<? super String> f8 = new Foo<<error></error>>("") {
        }; //new Foo<Object> created

        Foo<<error>String</error>> f9 = new Foo<<error></error>>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = new Foo<<error></error>>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Foo< ><error>("", "")</error>; //new Foo<Object> created
        Foo<? super String> f12 = new Foo<<error></error>>("", ""); //new Foo<Object> created

        Foo<<error>String</error>> f13 = new Foo<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<? extends String> f14 = new Foo<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<?> f15 = new Foo< ><error>("", "")</error> {
        }; //new Foo<Object> created
        Foo<? super String> f16 = new Foo<<error></error>>("", "") {
        }; //new Foo<Object> created
    }

    void testQualified() {
        Foo<<error>String</error>> f1 = new Neg02.Foo<<error></error>>(""); //new Foo<Integer> created
        Foo<? extends String> f2 = new Neg02.Foo<<error></error>>(""); //new Foo<Integer> created
        Foo<?> f3 = new Neg02.Foo< ><error>("")</error>; //new Foo<Object> created
        Foo<? super String> f4 = new Neg02.Foo<<error></error>>(""); //new Foo<Object> created

        Foo<<error>String</error>> f5 = new Neg02.Foo<<error></error>>("") {
        }; //new Foo<Integer> created
        Foo<? extends String> f6 = new Neg02.Foo<<error></error>>("") {
        }; //new Foo<Integer> created
        Foo<?> f7 = new Neg02.Foo< ><error>("")</error> {
        }; //new Foo<Object> created
        Foo<? super String> f8 = new Neg02.Foo<<error></error>>("") {
        }; //new Foo<Object> created

        Foo<<error>String</error>> f9 = new Neg02.Foo<<error></error>>("", ""); //new Foo<Integer> created
        Foo<? extends String> f10 = new Neg02.Foo<<error></error>>("", ""); //new Foo<Integer> created
        Foo<?> f11 = new Neg02.Foo< ><error>("", "")</error>; //new Foo<Object> created
        Foo<? super String> f12 = new Neg02.Foo<<error></error>>("", ""); //new Foo<Object> created

        Foo<<error>String</error>> f13 = new Neg02.Foo<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<? extends String> f14 = new Neg02.Foo<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Foo<?> f15 = new Neg02.Foo< ><error>("", "")</error> {
        }; //new Foo<Object> created
        Foo<? super String> f16 = new Neg02.Foo<<error></error>>("", "") {
        }; //new Foo<Object> created
    }
}

