class BarBase {
    {
        FooBase f = FooBase.EMPTY;<caret>
    }
}

class FooBase {
    public static final FooBase EMPTY;

    public static class FooBaseImpl extends FooBase {}


}