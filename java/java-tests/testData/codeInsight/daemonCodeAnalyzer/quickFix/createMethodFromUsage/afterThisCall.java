// "Create method 'f'" "true-preview"
public class A {
    public A() {
        this(f());
    }

    private static int f() {
        <caret><selection>return 0;</selection>
    }

    public A(int i) {
    }
}
