// "Remove redundant types" "true"
class ReturnTypeCompatibility {

    interface I1<L> {
        L m(L x);
    }

    static <P> void call(P p, I1<P> i2) {
        i2.m(null);
    }

    public static void main(String[] args) {
        call("", i -> "");
    }
}