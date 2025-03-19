// "Qualify static 'right(B)' call with reference to class 'Bug'" "true-preview"

class Bug<A, B> {
    public A getLeft() {
        return null;
    }

    public B getRight() {
        return null;
    }

    private boolean isRight() {
        return false;
    }

    public static <A, B> Bug<A, B> left(A a) {
        return null;
    }

    public static <A, B> Bug<A, B> right(B b) {
        return null;
    }

    public Bug<A, B> foo() {
        return isRight() ? Bug.<A, B>right(getRight()) : this.<A,B>left(getLeft());
    }
}