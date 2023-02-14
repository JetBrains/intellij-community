// "Create local variable 'a'" "true-preview"
public class A {
    void foo() {
        <caret>a = get();
    }

    Class<?>[] get() {return null;}
}
