// "Create local variable 'str'" "true"
public class A {
    void foo() {
        String s = s<caret>tr;
        str = null;
    }

}
