// "Access static 'I.m()' via class 'I' reference" "true-preview"
interface I {
    static void m() {}
}

class A implements I {
    {
        Runnable r = () -> I.m();
    }
}