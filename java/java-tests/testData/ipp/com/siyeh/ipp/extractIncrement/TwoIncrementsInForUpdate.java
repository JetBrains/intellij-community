class C {
    void f(char[] chars) {
        for (int i = 1, j = 0; i >= 0 && chars[i] <= ' '; i++, <caret>j++) {
        }
    }
}