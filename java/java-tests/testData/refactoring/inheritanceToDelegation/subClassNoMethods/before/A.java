public class A extends DelegatedBase{
    int methodFromA() {
        delegatedBaseMethod();
        return delegatedBaseField;
    }

    DelegatedBase getSomething() {
        return this;
    }
}
