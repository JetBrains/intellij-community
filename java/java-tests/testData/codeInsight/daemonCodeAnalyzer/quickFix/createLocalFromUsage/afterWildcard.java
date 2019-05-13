// "Create local variable 'a'" "true"
public class A {
    void foo() {
        Class<?>[] a = get();
    }

    Class<?>[] get() {return null;}
}
