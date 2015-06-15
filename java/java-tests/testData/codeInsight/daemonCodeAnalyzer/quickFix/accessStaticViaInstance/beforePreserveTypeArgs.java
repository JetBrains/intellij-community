// "Access static 'Bug.right(B)' via class 'Bug' reference" "true"

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
        return isRight() ? this.<A, B><caret>right(getRight()) : this.<A,B>left(getLeft());
    }
}