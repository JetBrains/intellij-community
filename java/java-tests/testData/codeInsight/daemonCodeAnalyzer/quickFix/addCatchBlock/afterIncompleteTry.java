// "Add 'catch' clause(s)" "true"
class Foo {
    void test(String s) {
        try {
            System.out.println(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
