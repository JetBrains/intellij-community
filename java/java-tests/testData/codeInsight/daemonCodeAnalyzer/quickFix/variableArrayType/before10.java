// "Change 'new Long[]' to 'new char[][]'" "true-preview"
class A {
    void m() {
        final char[][] test = new Long[]{<caret>{'a'}, {'1'}};
    }
}