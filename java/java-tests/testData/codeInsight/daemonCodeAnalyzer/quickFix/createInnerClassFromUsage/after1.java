// "Create inner class 'Abc'" "true-preview"
public class Test {
    public foo(int ppp) {
        <caret>Abc.foo();
    }

    private class Abc {
    }
}