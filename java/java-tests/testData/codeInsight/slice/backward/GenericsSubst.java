import java.util.Map;

class M<X, Y> {
    Map<X, Y> m;
    Y y;

    public M(Map<X, Y> m, Y y) {
        this.m = m;
        this.y = <flown11111>y;
    }

    public static <MX, MY> M<MX, MY> makeM(MY y) {
        return new M<MX, MY>(null, <flown111111>y);
    }

    Y get(X x) {
        Y res = <flown11121>m.get(x);
        return <flown111>res == null ? <flown1111>y : <flown1112>res;
    }

    public static void g() {
        String <caret>a = <flown1>f(M.<String, String>makeM(<flown1111111>"a"), "k");
    }

    public static <A> A f(M<A, A> a, A ka) {
        return <flown11>a.get(ka);
    }
}