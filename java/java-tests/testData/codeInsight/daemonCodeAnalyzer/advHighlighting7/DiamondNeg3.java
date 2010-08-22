class Neg03<U> {

            class Foo<V extends Number> {
                Foo(V x) {}
                <Z> Foo(V x, Z z) {}
            }

            void testSimple() {
                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<?> f3 = new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<?> f7 = new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>{}; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f9 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f10 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<?> f11 = new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f12 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f13 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f14 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<?> f15 = new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>{}; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f16 = new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Object> created
            }

            void testQualified_1() {
                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<?> f3 = new Neg03<U>.Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<?> f7 = new Neg03<U>.Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>{}; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f9 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f10 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<?> f11 = new Neg03<U>.Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f12 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Object> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f13 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f14 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<?> f15 = new Neg03<U>.Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>{}; //new Foo<Object> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f16 = new Neg03<U>.Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Object> created
            }

            void testQualified_2(Neg03<U> n) {
                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f1 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f2 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created
                Foo<?> f3 = n.new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f4 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""); //new Foo<Integer> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f5 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f6 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created
                Foo<?> f7 = n.new Foo<><error descr="'Foo(? extends java.lang.Number)' in 'Neg03.Foo' cannot be applied to '(java.lang.String)'">("")</error>{}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f8 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>(""){}; //new Foo<Integer> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f9 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f10 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created
                Foo<?> f11 = n.new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f12 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""); //new Foo<Integer> created

                Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>> f13 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? extends String' is not within its bound; should extend 'java.lang.Number'">? extends String</error>> f14 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
                Foo<?> f15 = n.new Foo<><error descr="'Foo(? extends java.lang.Number, java.lang.String)' in 'Neg03.Foo' cannot be applied to '(java.lang.String, java.lang.String)'">("", "")</error>{}; //new Foo<Integer> created
                Foo<<error descr="Type parameter '? super String' is not within its bound; should extend 'java.lang.Number'">? super String</error>> f16 = n.new Foo<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'"></error>>("", ""){}; //new Foo<Integer> created
            }
        }
