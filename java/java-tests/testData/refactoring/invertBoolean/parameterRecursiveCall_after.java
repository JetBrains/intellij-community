class InvertBooleanParameterTest {
    void foo(boolean bInverted) {
        boolean c = bInverted;
        foo(bInverted);
        foo(false);
    }

    {
        foo(false);
    }
}