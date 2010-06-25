// "Create Constructor" "true"
class Test {

    public void t() {
        new Inner(<caret>"a"){};
    }

    class Inner {
    }
}