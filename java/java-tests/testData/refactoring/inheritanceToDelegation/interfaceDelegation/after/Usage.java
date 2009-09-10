public class Usage {
    A a = new A();

    public void usage() {
        a.method1();
        a.getMyDelegate().method2();
        use(a.getMyDelegate());
    }

    private void use(Intf i) {
    }
}