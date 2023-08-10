// "Change variable 'test' type to 'int[][]'" "true-preview"
class A {
    void m() {
        Long test = new Long[][][][]{{<caret>1}, {2}};
    }
}