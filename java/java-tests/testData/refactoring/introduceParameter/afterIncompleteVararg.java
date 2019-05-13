class C {
    void method(final int anObject, String... s) {
        System.out.println(s[anObject]);
    }

    {
        method("a", "b", "c");
        method();
    }
}