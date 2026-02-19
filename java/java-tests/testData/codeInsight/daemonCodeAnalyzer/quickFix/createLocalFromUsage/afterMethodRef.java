// "Create local variable 'zeit'" "true-preview"
public class A {
    void foo() {
        Object zeit<caret> = A::foo;
    }

    static void foo(){}
}
