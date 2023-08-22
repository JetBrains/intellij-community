// "Create inner class 'Abc'" "true-preview"
public class Test {
    private void foo(<caret>Abc param) {}

    private class Abc {
    }
}