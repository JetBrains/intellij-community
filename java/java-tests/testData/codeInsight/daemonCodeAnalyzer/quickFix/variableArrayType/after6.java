// "Change 'test' type to 'char[]'" "true"
class A {
    void m() {
        final char[] test = {<caret>'a'};
    }
}