public class Test {
    public int <caret>foo(int p1, int p2) {
        p2++;
        return someMethod(p1, p2);
    }

    public void use1() {
        int r = foo(x, y);
    }

    public void use2() {
        int r = foo(field1, field1);
    }

    public void use3() {
        int r = foo(field2, field2);
    }

    public void use4() {
        int r = foo(field3, field3);
    }

    {
        field2++;
    }

    private final int field1;
    private int field2;
    private int field3;
}