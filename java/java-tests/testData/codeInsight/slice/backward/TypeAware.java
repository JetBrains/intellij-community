class A {
    private static class Tuple<X,Y> {
        public final X <flown11>x;
        public final Y y;
        public Tuple(X <flown1111>x,Y y) {this.x=<flown111>x; this.y=y;}
    }

    private static class Foo {
        void f() {
            Tuple<Integer,Integer> t1 = new Tuple<Integer,Integer>(1,2);
            Tuple<String,String> t2 = new Tuple<String,String>(<flown11111>"a","b");
            Tuple<Foo,Foo> t2w = new Tuple<Foo,Foo>(null,null);
            
            String <caret>x = t2.<flown1>x;
        }
    }
}