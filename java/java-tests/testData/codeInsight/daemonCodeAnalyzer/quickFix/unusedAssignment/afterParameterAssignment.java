// "Remove redundant assignment" "true"
class A {
    void m(int i) {
        i = 0;
        System.out.println(i);
    }
}