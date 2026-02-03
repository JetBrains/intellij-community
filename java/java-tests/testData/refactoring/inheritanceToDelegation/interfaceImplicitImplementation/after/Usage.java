public class Usage {
    A a = new A();

    public void usage() {
        a.getMyDelegate().method1();
        a.method2();
        use(a.getMyDelegate());
    }

    private void use(Intf i) {
    }
}