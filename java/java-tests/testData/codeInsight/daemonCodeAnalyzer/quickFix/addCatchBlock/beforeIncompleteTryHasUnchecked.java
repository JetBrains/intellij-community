// "Add 'catch' clause(s)" "true-preview"
class Foo {
    void test(String s) {
        try {
            System.out.println(Integer.parseInt(s));
        }<caret>
    }
}
