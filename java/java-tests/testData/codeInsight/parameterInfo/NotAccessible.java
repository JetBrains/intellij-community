class First {
    private void doFirst(int param) {
    }
}

class Second {
    private void doSecond(First first) {
        first.doFirst(<caret>);
    }
}