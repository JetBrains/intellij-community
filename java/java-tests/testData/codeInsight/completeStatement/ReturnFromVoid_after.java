
class Test {
    void test(Object o) {
        if (o == null) return;<caret>
        String foo = "foo";
    }
}