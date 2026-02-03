class Test {
    {
        String a = cast("str");
        Integer[] b = cast(new Integer[0]);
        Object[] c = cast(new Object[0]);
    }

    private <T> T cast(Object obj) {
        return (T)obj;
    }
}
