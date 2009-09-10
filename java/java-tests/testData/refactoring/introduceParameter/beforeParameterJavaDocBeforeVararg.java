class C {
    /**
     * @param s
     */
    void method(String... s) {
        System.out.println(s[<selection>0</selection>]);
    }

    {
        method("a", "b", "c");
        method();
    }
}