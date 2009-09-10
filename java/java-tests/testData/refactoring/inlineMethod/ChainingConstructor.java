class InlineMethodTest {
    public static InlineMethodTest createInstance() {
        return new InlineMethodTest(0);
    }

    protected <caret>InlineMethodTest(int y) {
        this("hello world", y);
    }

    protected InlineMethodTest() {
        this(0);
    }

    public InlineMethodTest(String text, int i) {
    }
}

class Derived extends InlineMethodTest {
    public Derived(int i) {
        super(i);
    }
}
