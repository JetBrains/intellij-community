// "Create Method 'f'" "true"
public class A {
    public A() {
        this(f());
    }

    private static int f() {
        <selection>return 0;  //To change body of created methods use File | Settings | File Templates.<caret></selection>
    }

    public A(int i) {
    }
}
