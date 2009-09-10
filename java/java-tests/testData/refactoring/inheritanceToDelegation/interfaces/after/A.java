public class A implements I {
    private final Base myDelegate = new Base();

    public Base getMyDelegate() {
        return myDelegate;
    }

    public void methodFromI() {
        myDelegate.methodFromI();
    }
}