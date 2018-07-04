package x;

class X {
    public String foo(String foo) {
        @SuppressWarnings("UnnecessaryLocalVariable")
        String bar = foo;
        return new String(bar);
    }
}
