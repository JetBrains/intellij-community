public class A {
    protected final DelegatedBase myDelegate = new DelegatedBase();

    int methodFromA() {
        myDelegate.delegatedBaseMethod();
        return myDelegate.delegatedBaseField;
    }

    DelegatedBase getSomething() {
        return myDelegate;
    }

    public DelegatedBase getMyDelegate() {
        return myDelegate;
    }
}
