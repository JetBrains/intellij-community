class Tuple<X,Y> {
    public final X x;
    public final Y y;
    public Tuple(X x,Y y) {this.x=x; this.y=y;}

    public X getX() {
        return <caret>x;
    }

    private static <X,Y> Tuple<X,Y> copy(Tuple<X,Y> t) {
        return new Tuple<X,Y>(t.x, t.y);
    }

}
