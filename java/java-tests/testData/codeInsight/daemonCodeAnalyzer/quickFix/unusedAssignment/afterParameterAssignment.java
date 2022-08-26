// "Remove redundant assignment" "true-preview"
class A {
    void m(int i) {
        i = 0;
        System.out.println(i);
    }
}