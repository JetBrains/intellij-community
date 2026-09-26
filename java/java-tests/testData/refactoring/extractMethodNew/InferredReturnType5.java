class Test {
    public double test(boolean b, Integer notNullInt) {
        <selection>if (b) {
            return notNullInt;
        }
        if (!b) {
            return 42.0;
        }</selection>
        return 42l;
    }
}