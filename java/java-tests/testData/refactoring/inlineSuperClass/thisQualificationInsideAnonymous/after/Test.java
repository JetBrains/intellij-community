class Test {
    protected final Object myBar;
    protected final Object myBizz;

    public Test() {
        Test.this.myBar = new Object() {
        };
        Test.this.myBizz = null;
    }
}