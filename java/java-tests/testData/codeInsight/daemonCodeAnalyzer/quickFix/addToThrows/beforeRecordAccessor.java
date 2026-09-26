// "Add exception to method signature" "false"
record X(int foo) {
    public int foo() {
        throw new <caret>Exception();
    }
}