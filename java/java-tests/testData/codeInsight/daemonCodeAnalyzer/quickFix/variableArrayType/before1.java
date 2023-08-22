// "Change variable 'test' type to 'int[][]'" "true-preview"
class A {
    void m() {
        final Long[][] test = {new int<caret>[]{1}};
    }
}