// "Create local variable 'zeit'" "true"
public class A {
    void foo() {
        String[] split = null;
        ze<caret>it//c1
          = split[1]; // 2011-04-13
    }

}
