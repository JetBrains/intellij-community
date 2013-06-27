// "Create Local Variable 'a'" "true"
public class A {
    void foo() {
        Class<?>[] a = get();
    }

    Class<?>[] get() {return null;}
}
