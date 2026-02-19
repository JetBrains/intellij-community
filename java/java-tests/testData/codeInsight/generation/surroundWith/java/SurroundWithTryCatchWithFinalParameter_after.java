class ModuleATest {
    {
        Exception ex = null;
        try {
            foo();
        } catch (final Smth ex1) {
            throw new RuntimeException(ex1);
        }
        Exception ex1 = null; 
    }

    void foo() throws Smth {}
    static class Smth extends Exception {}
}