class ModuleATest {
    {
        Exception ex = null;
        try {
            foo();
        } catch (final Smth ex1) {
            ex1.printStackTrace();
        }
        Exception ex1 = null; 
    }

    void foo() throws Smth {}
    static class Smth extends Exception {}
}