// "Create constructor" "true-preview"
class Test {

    public void t() {
        new Inner(<caret>"a"){};
    }

    class Inner {
    }
}