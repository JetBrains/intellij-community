class A {
    private int i;
    private int j;
    private String s;

    public A(int i, int j) {
        NewMethodResult x = newMethod(i);
        this.j = j;
    }

    NewMethodResult newMethod(int i) {
        this.i = i;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    public A(int i, String s) {
        this.i = i;
        this.s = s;
    }
}