// "Cast qualifier to 'java.lang.String'" "false"
class A {
    interface I<T> {
        void m(T t);
    }

    void method() {
        CharSequence cs = "";
        int p = foo(s -> {
            if (s instanceof String) {
                s.subs<caret>tring(1);
            }
        }, cs);
    }

    <K> K foo(I<K> i, K k) {
        return null;
    }
}