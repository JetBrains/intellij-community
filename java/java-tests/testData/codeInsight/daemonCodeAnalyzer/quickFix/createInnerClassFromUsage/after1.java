// "Create Inner Class 'Abc'" "true"
public class Test {
    public foo(int ppp) {
        <caret>Abc.foo();
    }

    private class Abc {
    }
}