// "Remove redundant assignment" "true-preview"
class A {
    void m(int i) {
        <caret>i = 9;
        i = 0;
        System.out.println(i);
    }
}