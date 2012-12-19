class Test {

    {
        f((Bar) getComponent("bar"));
        f1((Bar) getComponent("bar"));
        Bar b = (Bar) getComponent("bar");
    }

    private <J extends Bar> void f(J j) {}

    private <J> void f1(J j) {}

    private <T> T getComponent(String name) {
        return null;
    }

    static class Bar {}
}
