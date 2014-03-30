// "Create Local Variable 'a'" "true"
public class A {
    void foo() {
        <caret>a = get();
    }

    Class<?>[] get() {return null;}
}
