class A {
    private static class Tuple<X,Y> {
        public final X x;
        public final Y y;
        public Tuple(X x,Y y) {this.x=<flown11>x; this.y=y;}
    }

    private static class Foo {
        void f() {
            Tuple<Integer,Integer> t1 = new Tuple<Integer,Integer>(1,2);
            Tuple<String,String> t2 = new Tuple<String,String>(<flown111>"a","b");
            Tuple<Foo,Foo> t2w = new Tuple<Foo,Foo>(null,null);
            
            String <caret>x = t2.<flown1>x;
        }
    }
}