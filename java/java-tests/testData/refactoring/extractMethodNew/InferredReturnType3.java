class Test {
    public Number test(boolean b, Integer nullableInt) {
        <selection>if (b) {
            return nullableInt;
        }
        if (!b) {
            return 42.0;
        }
        return 42l;</selection>
    }
}