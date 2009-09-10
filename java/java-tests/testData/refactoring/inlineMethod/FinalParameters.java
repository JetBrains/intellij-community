class Test {
    void method(Object x) {
        String s = null;
        s = (String) x;
        toInline(s.length());
    }
    void toInli<caret>ne(final int i) {
        System.out.println(i);
    }
}
