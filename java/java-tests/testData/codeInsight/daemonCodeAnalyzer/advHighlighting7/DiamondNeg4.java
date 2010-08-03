        class Neg04 {

            void test() {
                class Foo<V extends Number> {
                    Foo(V x) {}
                    <Z> Foo(V x, Z z) {}
                }
                Foo<<error>String</error>> n1 = new Foo<<error></error>>(""); //new Foo<Integer> created
                Foo<? extends String> n2 = new Foo<<error></error>>(""); //new Foo<Integer> created
                Foo<?> n3 = new Foo<><error>("")</error>; //new Foo<Object> created
                Foo<? super String> n4 = new Foo<<error></error>>(""); //new Foo<Object> created

                Foo<<error>String</error>> n5 = new Foo<<error></error>>(""){}; //new Foo<Integer> created
                Foo<? extends String> n6 = new Foo<<error></error>>(""){}; //new Foo<Integer> created
                Foo<?> n7 = new Foo<><error>("")</error>{}; //new Foo<Object> created
                Foo<? super String> n8 = new Foo<<error></error>>(""){}; //new Foo<Object> created

                Foo<<error>String</error>> n9 = new Foo<<error></error>>("", ""); //new Foo<Integer> created
                Foo<? extends String> n10 = new Foo<<error></error>>("", ""); //new Foo<Integer> created
                Foo<?> n11 = new Foo<><error>("", "")</error>; //new Foo<Object> created
                Foo<? super String> n12 = new Foo<<error></error>>("", ""); //new Foo<Object> created

                Foo<<error>String</error>> n13 = new Foo<<error></error>>("", ""){}; //new Foo<Integer> created
                Foo<? extends String> n14 = new Foo<<error></error>>("", ""){}; //new Foo<Integer> created
                Foo<?> n15 = new Foo<><error>("", "")</error>{}; //new Foo<Object> created
                Foo<? super String> n16 = new Foo<<error></error>>("", ""){}; //new Foo<Object> created
            }
        }
