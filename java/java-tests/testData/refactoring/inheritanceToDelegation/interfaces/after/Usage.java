class Usage {
    private A myA = new A();
    public void methodExpectingI(I i) {
        i.methodFromI();
    }

    public J methodReturningJ() {
        return myA.getMyDelegate();
    }

    public void methodExpectingJ(J j) {
        j.methodFromJ();
    }

    public void main() {
        A a = new A();
        a.methodFromI();
        a.getMyDelegate().methodFromJ();
        methodExpectingI(a);
        methodExpectingJ(a.getMyDelegate());
        methodExpectingJ(myA.getMyDelegate());
    }
}