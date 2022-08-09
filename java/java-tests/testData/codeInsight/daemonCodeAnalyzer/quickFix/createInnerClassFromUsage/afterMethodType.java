// "Create inner class 'Abc'" "true-preview"
public class Test {
    private <caret>Abc foo() {}

    private class Abc {
    }
}