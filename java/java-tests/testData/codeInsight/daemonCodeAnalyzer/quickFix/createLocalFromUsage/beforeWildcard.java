// "Create local variable 'a'" "true"
public class A {
    void foo() {
        <caret>a = get();
    }

    Class<?>[] get() {return null;}
}
