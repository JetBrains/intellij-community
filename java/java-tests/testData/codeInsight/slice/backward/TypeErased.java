class A {
    private static class Tuple<X,Y> {
        public final X <flown11>x;
        public final Y y;
        public Tuple(X <flown1111>x,Y y) {this.x=<flown111>x; this.y=y;}
    }

    private static class Foo {
        void f2(Tuple<String, String> t2w) {
            Tuple t2 = new Tuple(1,2);
            String <caret>x = t2w.<flown1>x;
        }
    }
}