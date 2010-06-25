// "Change 'test' type to 'int[][]'" "true"
class A {
    void m() {
        Long test = new Long[][][][]{{<caret>1}, {2}};
    }
}