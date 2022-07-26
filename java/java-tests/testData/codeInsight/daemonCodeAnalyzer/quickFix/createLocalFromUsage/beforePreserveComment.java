// "Create local variable 'zeit'" "true-preview"
public class A {
    void foo() {
        String[] split = null;
        ze<caret>it//c1
          = split//c2
          [1]; // 2011-04-13
    }

}
