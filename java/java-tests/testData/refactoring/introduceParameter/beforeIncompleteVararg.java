class C {
    void method(int k, String... s) {
        System.out.println(s[<selection>k</selection>]);
    }

    {
        method("a", "b", "c");
        method();
    }
}