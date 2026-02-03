// "Add constructor parameter" "true"
class Test {
    private final String s;
    private final int i;

    Test(String s) {
        this.s = s;
        i = 0;
    }

    Test(int i) {
        this.i = i;<caret>
        s = "s";
    }
}