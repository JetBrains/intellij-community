class Test {
    public Number test(boolean b) {
        <selection>if (b) {
            return 42;
        }
        if (!b) {
            return 42.0;
        }</selection>
        return 42l;
    }
}