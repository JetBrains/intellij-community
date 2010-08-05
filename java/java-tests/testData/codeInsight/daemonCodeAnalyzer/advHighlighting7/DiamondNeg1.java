class Neg01<X extends Number> {

    Neg01(X x) {
    }

    <Z> Neg01(X x, Z z) {
    }

    void test() {
        Neg01<<error>String</error>> n1 = new Neg01<<error></error>>("" ); //new Foo<Integer> created
        Neg01<? extends String> n2 = new Neg01<<error></error>>(""); //new Foo<Integer> created
        Neg01<?> n3 = new Neg01<><error>("")</error>; //new Foo<Object> created
        Neg01<? super String> n4 = new Neg01<<error></error>>(""); //new Foo<Object> created

        Neg01<<error>String</error>> n5 = new Neg01<<error></error>>("") {
        }; //new Foo<Integer> created
        Neg01<? extends String> n6 = new Neg01<<error></error>>("") {
        }; //new Foo<Integer> created
        Neg01<?> n7 = new Neg01<><error>("")</error> {
        }; //new Foo<Object> created
        Neg01<? super String> n8 = new Neg01<<error></error>>("") {
        }; //new Foo<Object> created

        Neg01<<error>String</error>> n9 = new Neg01<<error></error>>("", ""); //new Foo<Integer> created
        Neg01<? extends String> n10 = new Neg01<<error></error>>("", ""); //new Foo<Integer> created
        Neg01<?> n11 = new Neg01<><error>("", "")</error>; //new Foo<Object> created
        <error>Foo</error><? super String> n12 = new Neg01<<error></error>>("", ""); //new Foo<Object> created

        Neg01<<error>String</error>> n13 = new Neg01<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Neg01<? extends String> n14 = new Neg01<<error></error>>("", "") {
        }; //new Foo<Integer> created
        Neg01<?> n15 = new Neg01<><error>("", "")</error> {
        }; //new Foo<Object> created
        Neg01<? super String> n16 = new Neg01<<error></error>>("", "") {
        }; //new Foo<Object> created
    }
}
