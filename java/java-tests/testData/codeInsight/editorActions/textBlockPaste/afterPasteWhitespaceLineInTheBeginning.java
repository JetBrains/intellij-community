public class A {
    public void f() {
        s("""
                
                
                first line
                second line<caret>""");
    }

    public void s(String s) {
    }
}