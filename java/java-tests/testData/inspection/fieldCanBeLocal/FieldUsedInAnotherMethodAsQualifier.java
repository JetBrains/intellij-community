class D {
    D first;
}

class Test {
    private D myDSettings;

    protected void setUp() {
        myDSettings = null;
    }

    public void testModuleCycle() {
        myDSettings.first = null;
    }
}