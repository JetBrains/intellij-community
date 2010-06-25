// "Change 'test' type to 'int[][]'" "true"
class A {
    void m() {
        int[][] test = new int[][]{<caret>{1}, {2}};
    }
}