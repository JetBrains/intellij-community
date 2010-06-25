// "Change 'test' type to 'char[]'" "true"
class A {
    void m() {
        final Long[] test = new Long[]{<caret>'a'};
    }
}