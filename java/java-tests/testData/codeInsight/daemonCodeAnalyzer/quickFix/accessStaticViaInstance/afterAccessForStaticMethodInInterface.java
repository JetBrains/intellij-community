// "Access static 'I.m()' via class 'I' reference" "true"
interface I {
    static void m() {}
}

class A implements I {
    {
        I.m();
    }
}