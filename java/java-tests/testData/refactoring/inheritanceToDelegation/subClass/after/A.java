public class A {
    private final DelegatedBase myDelegate = new DelegatedBase();

    int methodFromA() {
        delegatedBaseMethod();
        return myDelegate.delegatedBaseField;
    }

    DelegatedBase getSomething() {
        return myDelegate;
    }

    public DelegatedBase getMyDelegate() {
        return myDelegate;
    }

    void delegatedBaseMethod() {
        myDelegate.delegatedBaseMethod();
    }
}
