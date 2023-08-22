class Test {
    public Number test(boolean b, Integer nullableInt) {
        return newMethod(b, nullableInt);
    }

    private Number newMethod(boolean b, Integer nullableInt) {
        if (b) {
            return nullableInt;
        }
        if (!b) {
            return 42.0;
        }
        return 42l;
    }
}