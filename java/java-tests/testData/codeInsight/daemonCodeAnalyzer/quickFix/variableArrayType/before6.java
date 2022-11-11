// "Change variable 'test' type to 'char[]'" "true-preview"
class A {
    void m() {
        final Long[] test = {<caret>'a'};
    }
}