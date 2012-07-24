class A {
    private String toInline(boolean b) {
        if (b) {
            return "b";
        }
        return "a";
    }

    public String method(boolean b) {
        return <caret>toInline(b);
    }
}