// "Create Local Variable 'zeit'" "true"
public class A {
    void foo() {
        Object zeit<caret> = A::foo;
    }

    static void foo(){}
}
