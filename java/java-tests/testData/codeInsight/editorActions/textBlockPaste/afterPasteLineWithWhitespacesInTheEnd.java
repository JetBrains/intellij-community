public class A {
    public void f() {
        s("""
                first line with whitespaces in the end           \s
                second line with whitespaces in the end    \s<caret>""");
    }

    public void s(String s) {
    }
}