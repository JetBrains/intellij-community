// "Change 'test' type to 'int[][]'" "true"
class A {
    void m() {
        final Long[][] test = {new int<caret>[]{1}};
    }
}