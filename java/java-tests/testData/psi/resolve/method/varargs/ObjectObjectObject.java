class XY {
    public static void foo(Object... x) { }
    public static void foo(Object a, Object o, Object... x) { }

    public static void main(String[] args) {
        <ref>foo("a", "b");
    }
}
