class BarBase {
    {
        FooBase f = <caret>
    }
}

class FooBase {
    public static final FooBase EMPTY;

    public static class FooBaseImpl extends FooBase {}


}