class A {

    public void bar() {
        m("");
    }

    private Object f<caret>oo() {
        return "";
    }

    public void m(String ss) {}
}