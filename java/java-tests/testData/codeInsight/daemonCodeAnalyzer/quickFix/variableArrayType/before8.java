// "Change 'test' type to 'char[][]'" "false"
class A {
    void m() {
        final Long[] test = {<caret>{'a'}, {1}};
    }
}