// "Create inner class 'Abc'" "false"
public class Test {
    public void foo(int ppp) {
        int local = ppp + <caret>Abc;
    }
}