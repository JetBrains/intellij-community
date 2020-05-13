class A {
    public void test() {
        newMethod();
        int count;
        count=0;
        for(int j=0; j<100; j++) count++;
    }

    private void newMethod() {
        int count=0;
        for(int j=0; j<100; j++) count++;
    }
}