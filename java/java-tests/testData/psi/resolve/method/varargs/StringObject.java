
class XY {
    public static void foo(String... x) { }
    public static void foo(Object a, Object... x) { }

    public static void main(String[] args) {
        <caret>foo("a");
    }
}
