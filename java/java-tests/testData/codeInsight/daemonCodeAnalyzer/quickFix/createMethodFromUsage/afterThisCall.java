// "Create Method 'f'" "true"
public class A {
    public A() {
        this(f());
    }

    private static int f() {
        <caret><selection>return 0;  //To change body of created methods use File | Settings | File Templates.</selection>
    }

    public A(int i) {
    }
}
