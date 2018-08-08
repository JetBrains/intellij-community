class Test {
    protected final Object myBar;
    protected final Object myBizz;

    public Test() {
        this.myBar = new Object() {
        };
        this.myBizz = null;
    }
}