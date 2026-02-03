// "Add constructor parameter" "true"
class Test {
    private final String s;
    private final int i<caret>;

    Test(String s) {
        this.s = s;
        i = 0;
    }

    Test() {
        s = "s";
    }
}