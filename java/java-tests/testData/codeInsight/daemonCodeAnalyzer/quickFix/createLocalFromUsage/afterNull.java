// "Create local variable 'str'" "true"
public class A {
    void foo() {
        String str;
        String s = str;
        str = null;
    }

}
