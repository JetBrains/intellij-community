class ModuleATest {
    {
        Exception ex = null;
        f<caret>oo();
        Exception ex1 = null; 
    }

    void foo() throws Smth {}
    static class Smth extends Exception {}
}