// "Create constructor matching super" "true"
public class Parent {
    protected Parent(int x) { }
}

final class <caret>Derived extends Parent {
}