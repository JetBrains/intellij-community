// "Access static 'I.m()' via class 'I' reference" "true"
interface I {
    static void m() {}
}

class A {
    void f(I i){
        i.<caret>m();
    }
}