// "Add 'catch' clause(s)" "true"
class Foo {
    void test(String s) {
        try {
            System.out.println(Integer.parseInt(s));
        }<caret>
    }
}
