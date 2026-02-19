// "Change variable 'test' type to 'int[][]'" "true-preview"
class A {
    void m() {
        int[][] test = new int[][]{<caret>{1}, {2}};
    }
}