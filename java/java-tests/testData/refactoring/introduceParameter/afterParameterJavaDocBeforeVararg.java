class C {
    /**
     * @param anObject
     * @param s
     */
    void method(final int anObject, String... s) {
        System.out.println(s[anObject]);
    }

    {
        method(0, "a", "b", "c");
        method(0);
    }
}