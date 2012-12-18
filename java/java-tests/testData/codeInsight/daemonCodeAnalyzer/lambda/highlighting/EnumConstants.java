enum Bug {
    INSTANCE1((Integer x, Integer y) -> x.byteValue()),
    INSTANCE2((x, y) -> y. byteValue());
    private Bug(Foo foo) {
    }

    private interface Foo {
        byte bar(Integer x, Integer y);
    }
}
