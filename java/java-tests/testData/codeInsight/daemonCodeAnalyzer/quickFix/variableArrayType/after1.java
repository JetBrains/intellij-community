// "Change 'test' type to 'int[][]'" "true"
class A {
    void m() {
        final int[][] test = {new int<caret>[]{1}};
    }
}