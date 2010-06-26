// "Create Field 'FIELD'" "true"
class C {
    public void foo() {
        int i = ITest.FIELD<caret>;
    }
    
    public static interface ITest {
    }
}