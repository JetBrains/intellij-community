// "Create local variable 'zeit'" "true-preview"
public class A {
    void foo() {
        ze<caret>it = A::foo;
    }

    static void foo(){}
}
