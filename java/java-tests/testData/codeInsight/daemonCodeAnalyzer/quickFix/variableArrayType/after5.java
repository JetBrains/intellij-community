// "Change variable 'test' type to 'char[]'" "true-preview"
class A {
    void m() {
        final char[] test = new char[]{<caret>'a'};
    }
}