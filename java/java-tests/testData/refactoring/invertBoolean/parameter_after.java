class InvertBooleanParameterTest {
    void foo(boolean bInverted) {
        boolean c = bInverted;
    }

    {
        foo(false);
    }
}