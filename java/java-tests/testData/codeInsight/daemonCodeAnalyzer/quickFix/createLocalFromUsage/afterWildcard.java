// "Create local variable 'a'" "true-preview"
public class A {
    void foo() {
        Class<?>[] a = get();
    }

    Class<?>[] get() {return null;}
}
