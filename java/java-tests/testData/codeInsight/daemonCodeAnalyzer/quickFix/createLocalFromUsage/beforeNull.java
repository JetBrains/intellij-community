// "Create local variable 'str'" "true-preview"
public class A {
    void foo() {
        String s = s<caret>tr;
        str = null;
    }

}
