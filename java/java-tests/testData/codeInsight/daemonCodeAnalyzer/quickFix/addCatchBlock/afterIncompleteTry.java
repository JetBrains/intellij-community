// "Add 'catch' clause(s)" "true-preview"
class Foo {
    void test(String s) {
        try {
            System.out.println(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
