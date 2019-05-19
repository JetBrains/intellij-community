class Foo {

    protected String foo1(Object foo, String keyOne) {
        final Object[] objects = new Object[]{""};
        final Object o = bar2(keyOne, objects);
        return bar1(foo, o, "");
    }

    protected String bar1(Object foo, final Object anObject, Object... loggingItems) {
        assert foo != null;
        assert loggingItems != null;

        Object keyTwo = anObject;

        assert keyTwo != null;
        return null;
    }


    private Object bar2(String keyOne, Object[] loggingItems) {
        return null;
    }

}