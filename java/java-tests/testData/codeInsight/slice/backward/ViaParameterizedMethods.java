class A {
    private static class Tuple<X,Y> {
        public final X <flown11>x;
        public final Y y;
        public Tuple(X <flown1111>x,Y y) {this.x=<flown111>x; this.y=y;}
        public static <X1,Y1> Tuple<X1,Y1> create(X1 <flown111111>x, Y1 y) {
            return new Tuple<X1,Y1>(<flown11111>x, y);
        }
    }

    private static class Foo {
        void f() {
            Tuple<String,String> t = Tuple.create(<flown1111111>"","");
            Tuple<Object,Object> t2 = Tuple.create((Object) "",(Object) "");

            String <caret>x = t.<flown1>x;
        }
    }
}