class InvertBooleanParameterTest {
    void foo(boolean bInverted) {
        boolean c = bInverted;
        bInverted = true;
    }

    {
        foo(false);
    }
}

class Drv extends InvertBooleanParameterTest {
    void foo(boolean bInverted) {
        super.foo(bInverted);
    }
}