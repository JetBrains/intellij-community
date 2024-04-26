// "Qualify static 'm()' call with reference to class 'I'" "true-preview"
interface I {
    static void m() {}
}

class A implements I {
    {
        Runnable r = () -> I.m();
    }
}