// "Create constructor matching super" "true"
public class Parent {
    protected Parent(int x) { }
}

final class Derived extends Parent {
    Derived(int x) {
        super(x);
    }
}