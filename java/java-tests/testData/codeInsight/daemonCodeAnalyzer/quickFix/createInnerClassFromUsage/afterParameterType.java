// "Create inner class 'Abc'" "true"
public class Test {
    private void foo(<caret>Abc param) {}

    private class Abc {
    }
}