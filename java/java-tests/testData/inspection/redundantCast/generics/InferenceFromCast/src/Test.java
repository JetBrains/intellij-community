class Test {

    {
        f((Bar) getComponent());
        f1((Bar) getComponent());
        Bar b = (Bar) getComponent();
    }

    private <J extends Bar> void f(J j) {}

    private <J> void f1(J j) {}

    private <T> T getComponent() {
        return null;
    }

    static class Bar {}
}
