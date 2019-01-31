class Foo {

    protected String foo1(Object foo, String keyOne) {
        return bar1(foo, keyOne, "");
    }

    protected String bar1(Object foo, String keyOne, Object... loggingItems) {
        assert foo != null;
        assert loggingItems != null;

        Object keyTwo = <selection>bar2(keyOne, loggingItems)</selection>;

        assert keyTwo != null;
        return null;
    }


    private Object bar2(String keyOne, Object[] loggingItems) {
        return null;
    }

}