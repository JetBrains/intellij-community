// "Create local variable 'str'" "true-preview"
public class A {
    void foo() {
        String str;
        String s = str;
        str = null;
    }

}
