abstract class A{
    static boolean isB<caret>ool() {
        return false;
    }
    
    
    interface I {
        boolean b();
    }
    
    {
        I i = A::isBool;
    }
}
