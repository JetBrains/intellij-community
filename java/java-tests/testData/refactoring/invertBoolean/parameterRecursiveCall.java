class InvertBooleanParameterTest {
    void foo(boolean <caret>b) {
        boolean c = !b;
        foo(b);
        foo(true);
    }

    {
        foo(true);
    }
}