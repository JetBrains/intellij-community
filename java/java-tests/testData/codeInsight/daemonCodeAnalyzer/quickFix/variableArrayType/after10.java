// "Change 'new Long[]' to 'new char[][]'" "true"
class A {
    void m() {
        final char[][] test = new char[][]{<caret>{'a'}, {'1'}};
    }
}