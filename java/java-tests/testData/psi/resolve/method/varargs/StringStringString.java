class XY {
    public static void foo(String... x) { }
    public static void foo(String a, String... x) { }

    public static void main(String[] args) {
        <ref>foo("a");
    }
}
