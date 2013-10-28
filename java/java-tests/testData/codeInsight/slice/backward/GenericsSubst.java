class M<X, Y> {
  interface Map<X,Y> {
    Y get(X x);
  }
    Map<X, Y> m;
    Y <flown11111>y;

    public M(Map<X, Y> m, Y <flown1111111>y) {
        this.m = m;
        this.y = <flown111111>y;
    }

    public static <MX, MY> M<MX, MY> makeM(MY <flown111111111>y) {
        return new M<MX, MY>(null, <flown11111111>y);
    }

    Y get(X x) {
        Y res = <flown11121>m.get(x);
        return <flown111>res == null ? <flown1111>y : <flown1112>res;
    }

    public static void g() {
        String <caret>a = <flown1>f(M.<String, String>makeM(<flown1111111111>"a"), "k");
    }

    public static <A> A f(M<A, A> a, A ka) {
        return <flown11>a.get(ka);
    }
}