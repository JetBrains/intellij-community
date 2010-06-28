class A {
    private static class Tuple<X,Y> {
        public final X <caret>x;
        public final Y y;
        public Tuple(X x,Y y) {this.x=<flown1>x; this.y=y;}
    }

    private static class Foo {
        void f() {
            Tuple<Integer,Integer> t1 = new Tuple<Integer,Integer>(<flown11>1,2);
            Tuple<String,String> t2 = new Tuple<String,String>(<flown12>"a","b");
            String x = t2.x;
        }
    }
}